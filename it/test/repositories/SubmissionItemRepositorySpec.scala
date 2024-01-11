/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories

import cats.implicits.toTraverseOps
import models.dmsa._
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import play.api.{Configuration, Environment}
import repositories.SubmissionItemRepository.NothingToUpdateException
import util.MutableClock
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, LocalDate}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class SubmissionItemRepositorySpec extends AnyFreeSpec
  with Matchers with OptionValues
  with DefaultPlayMongoRepositorySupport[SubmissionItem]
  with ScalaFutures with IntegrationPatience
  with BeforeAndAfterEach {

  private val configuration: Configuration =
    Configuration.load(Environment.simple())

  private implicit val crypto: Encrypter with Decrypter =
    SymmetricCryptoFactory.aesGcmCryptoFromConfig("crypto", configuration.underlying)

  private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val clock = MutableClock(now)

  override def beforeEach(): Unit = {
    super.beforeEach()
    clock.set(now)
  }

  override protected val repository = new SubmissionItemRepository(
    mongoComponent = mongoComponent,
    clock = clock,
    configuration = Configuration("lock-ttl" -> 30)
  )

  private val item = SubmissionItem(
    id = "id",
    status = SubmissionItemStatus.Submitted,
    objectSummary = ObjectSummary(
      location = "location",
      contentLength = 1337,
      contentMd5 = "hash",
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    ),
    failureReason = None,
    metadata = Metadata("nino", clock.instant(), "correlationId"),
    created = clock.instant().minus(1, ChronoUnit.DAYS),
    lastUpdated = clock.instant().minus(1, ChronoUnit.DAYS),
    sdesCorrelationId = "correlationId"
  )

  "insert" - {

    "must insert a new record and return successfully" in {
      val expected = item.copy(lastUpdated = clock.instant())
      repository.get("id").futureValue mustBe None
      repository.insert(item).futureValue
      repository.get("id").futureValue.value mustEqual expected
    }

    "must fail to insert an item for an existing id and owner" in {
      repository.insert(item).futureValue
      repository.insert(item.copy(sdesCorrelationId = "foobar")).failed.futureValue
    }

    "must fail to insert an item with an existing sdesCorrelationId" in {
      repository.insert(item).futureValue
      repository.insert(item.copy(id = "bar")).failed.futureValue
    }
  }

  "get by id" - {

    "must return an item that matches the id" in {
      repository.insert(item).futureValue
      repository.insert(item.copy(id = "id2", sdesCorrelationId = "correlationId2")).futureValue
      repository.get("id").futureValue.value mustEqual item.copy(lastUpdated = clock.instant())
    }

    "must return `None` when there is no item matching the id" in {
      repository.insert(item).futureValue
      repository.get("foobar").futureValue mustNot be(defined)
    }
  }

  "get by sdes correlation id" - {

    "must return an item that matches the id" in {
      repository.insert(item).futureValue
      repository.insert(item.copy(id = "id2", sdesCorrelationId = "correlationId2")).futureValue
      repository.getByCorrelationId(item.sdesCorrelationId).futureValue.value mustEqual item.copy(lastUpdated = clock.instant())
    }

    "must return `None` when there is no item matching the id" in {
      repository.insert(item).futureValue
      repository.getByCorrelationId("foobar").futureValue mustNot be(defined)
    }
  }

  "update by id" - {

    "must update a record if it exists and return it" in {
      val expected = item.copy(status = SubmissionItemStatus.Submitted, failureReason = Some("failure"), lastUpdated = clock.instant())
      repository.insert(item).futureValue
      repository.update("id", SubmissionItemStatus.Submitted, failureReason = Some("failure")).futureValue mustEqual expected
      repository.get("id").futureValue.value mustEqual expected
    }

    "must fail if no record exists" in {
      repository.insert(item).futureValue
      repository.update("foobar", SubmissionItemStatus.Submitted, failureReason = Some("failure")).failed.futureValue mustEqual NothingToUpdateException
    }

    "must remove failure reason if it's passed as `None`" in {
      val newItem = item.copy(failureReason = Some("failure"))
      val expected = item.copy(lastUpdated = clock.instant())
      repository.insert(newItem).futureValue
      repository.update("id", SubmissionItemStatus.Submitted, failureReason = None).futureValue
      repository.get("id").futureValue.value mustEqual expected
    }

    "must succeed when there is no failure reason to remove" in {
      val expected = item.copy(lastUpdated = clock.instant())
      repository.insert(item).futureValue
      repository.update("id", SubmissionItemStatus.Submitted, failureReason = None).futureValue
      repository.get("id").futureValue.value mustEqual expected
    }
  }

  "lockAndReplaceOldestItemByStatus" - {

    "must return Found and replace an item that is found" in {

      val item1 = randomItem
      val item2 = randomItem

      repository.insert(item1).futureValue
      clock.advance(Duration.ofMinutes(1))
      repository.insert(item2).futureValue
      clock.advance(Duration.ofMinutes(1))

      repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { item =>
        Future.successful(item.copy(status = SubmissionItemStatus.Completed))
      }.futureValue mustEqual QueryResult.Found

      val updatedItem1 = repository.get(item1.id).futureValue.value
      updatedItem1.status mustEqual SubmissionItemStatus.Completed
      updatedItem1.lastUpdated mustEqual clock.instant()
      updatedItem1.lockedAt mustBe None

      val updatedItem2 = repository.get(item2.id).futureValue.value
      updatedItem2.status mustEqual SubmissionItemStatus.Submitted
      updatedItem2.lastUpdated mustEqual item2.lastUpdated.plus(Duration.ofMinutes(1))
      updatedItem2.lockedAt mustBe None
    }

    "must return NotFound and not replace when an item is not found" in {
      repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { item =>
        Future.successful(item)
      }.futureValue mustEqual QueryResult.NotFound
    }

    "must return NotFound and not replace when an item is locked" in {

      val item = randomItem.copy(lockedAt = Some(clock.instant()))

      repository.insert(item).futureValue
      clock.advance(Duration.ofSeconds(29))

      repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { item =>
        Future.successful(item.copy(status = SubmissionItemStatus.Completed))
      }.futureValue mustEqual QueryResult.NotFound

      val retrievedItem = repository.get(item.id).futureValue.value
      retrievedItem.status mustEqual SubmissionItemStatus.Submitted
      retrievedItem.lastUpdated mustEqual item.lastUpdated
      retrievedItem.lockedAt mustBe item.lockedAt
    }

    "must ignore locks that are too old" in {

      val item1 = randomItem.copy(lockedAt = Some(clock.instant().minus(Duration.ofMinutes(30))))
      val item2 = randomItem

      repository.insert(item1).futureValue
      clock.advance(Duration.ofMinutes(1))
      repository.insert(item2).futureValue
      clock.advance(Duration.ofMinutes(1))

      repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { item =>
        Future.successful(item.copy(status = SubmissionItemStatus.Completed))
      }.futureValue mustEqual QueryResult.Found

      val updatedItem1 = repository.get(item1.id).futureValue.value
      updatedItem1.status mustEqual SubmissionItemStatus.Completed
      updatedItem1.lastUpdated mustEqual clock.instant()
      updatedItem1.lockedAt mustBe None

      val updatedItem2 = repository.get(item2.id).futureValue.value
      updatedItem2.status mustEqual SubmissionItemStatus.Submitted
      updatedItem2.lastUpdated mustEqual item2.lastUpdated.plus(Duration.ofMinutes(1))
      updatedItem2.lockedAt mustBe None
    }

    "must lock item while the provided function runs" in {

      val promise: Promise[SubmissionItem] = Promise()
      val item = randomItem

      repository.insert(item).futureValue
      clock.advance(Duration.ofMinutes(1))

      val runningFuture = repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { _ =>
        promise.future
      }

      eventually(Timeout(Span(30, Seconds))) {
        repository.get(item.id).futureValue.value.lockedAt.value mustEqual clock.instant()
        promise.success(item.copy(status = SubmissionItemStatus.Completed))
        runningFuture.futureValue
        repository.get(item.id).futureValue.value.lockedAt mustBe None
      }
    }

    "must not unlock item if the provided function fails" in {

      val promise: Promise[SubmissionItem] = Promise()
      repository.insert(item).futureValue
      clock.advance(Duration.ofMinutes(1))

      val runningFuture = repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { _ =>
        promise.future
      }

      eventually(Timeout(Span(30, Seconds))) {
        repository.get(item.id).futureValue.value.lockedAt.value mustEqual clock.instant()
        promise.failure(new RuntimeException())
        runningFuture.failed.futureValue
        repository.get(item.id).futureValue.value.lockedAt.value mustEqual clock.instant()
      }
    }
  }

  "dailySummaries" - {

    "must return a summary for every day where there are records" in {

      List(
        randomItem.copy(status = SubmissionItemStatus.Completed, created = clock.instant()),
        randomItem.copy(status = SubmissionItemStatus.Completed, created = clock.instant()),
        randomItem.copy(status = SubmissionItemStatus.Failed, created = clock.instant()),
        randomItem.copy(status = SubmissionItemStatus.Submitted, created = clock.instant()),
        randomItem.copy(status = SubmissionItemStatus.Forwarded, created = clock.instant()),
        randomItem.copy(status = SubmissionItemStatus.Completed, created = clock.instant().minus(Duration.ofDays(10)))
      ).traverse(repository.insert)
        .futureValue

      val result = repository.dailySummaries.futureValue

      result must contain theSameElementsAs List(
        DailySummary(date = LocalDate.now(clock), submitted = 1, forwarded = 1, failed = 1, completed = 2),
        DailySummary(date = LocalDate.now(clock).minusDays(10), submitted = 0, forwarded = 0, failed = 0, completed = 1)
      )
    }
  }

  "list" - {

    "must return a list of submission summaries" in {

      val item1 = item.copy(id = "id1", sdesCorrelationId = "correlationId1")
      val item2 = item.copy(id = "id2", sdesCorrelationId = "correlationId2")

      insert(item1).futureValue
      insert(item2).futureValue

      val result = repository.list().futureValue
      result.summaries must contain theSameElementsAs Seq(SubmissionSummary(item1), SubmissionSummary(item2))
      result.totalCount mustEqual 2
    }

    "must return a list of items filtered by status when status is supplied" in {

      val item1 = item.copy(id = "id1", sdesCorrelationId = "correlationId1", status = SubmissionItemStatus.Failed)
      val item2 = item.copy(id = "id2", sdesCorrelationId = "correlationId2")

      List(item1, item2).traverse(insert).futureValue

      val result = repository.list(status = Some(SubmissionItemStatus.Failed)).futureValue
      result.summaries must contain only SubmissionSummary(item1)
      result.totalCount mustEqual 1
    }

    "must return a list of items filtered by the day the item was created" in {

      val item1 = item.copy(id = "id1", sdesCorrelationId = "correlationId1", created = clock.instant())
      val item2 = item.copy(id = "id2", sdesCorrelationId = "correlationId2", created = clock.instant().minus(Duration.ofDays(1)))

      List(item1, item2).traverse(insert).futureValue

      val result = repository.list(created = Some(LocalDate.now(clock))).futureValue
      result.summaries must contain only SubmissionSummary(item1)
      result.totalCount mustEqual 1
    }

    "must apply all filters" in {

      val item1 = randomItem.copy(created = clock.instant().minus(Duration.ofDays(2)), status = SubmissionItemStatus.Completed)

      List(
        item1,
        randomItem.copy(created = clock.instant(), status = SubmissionItemStatus.Completed),
        randomItem.copy(created = clock.instant().minus(Duration.ofDays(2)), status = SubmissionItemStatus.Forwarded),
      ).traverse(insert).futureValue

      val result = repository.list(created = Some(LocalDate.now(clock).minusDays(2)), status = Some(SubmissionItemStatus.Completed)).futureValue
      result.summaries must contain only SubmissionSummary(item1)
      result.totalCount mustEqual 1
    }

    "must limit the number of items to the supplied limit" in {

      val item1 = randomItem.copy(created = clock.instant())
      clock.advance(Duration.ofMinutes(1))
      val item2 = randomItem.copy(created = clock.instant())

      List(item1, item2).traverse(insert).futureValue

      val result = repository.list(limit = 1).futureValue
      result.summaries must contain only SubmissionSummary(item2)
      result.totalCount mustEqual 2
    }

    "must return items offset by the offset" in {

      val item1 = randomItem.copy(created = clock.instant())
      clock.advance(Duration.ofMinutes(1))
      val item2 = randomItem.copy(created = clock.instant())

      List(item1, item2).traverse(insert).futureValue

      val result = repository.list(limit = 1, offset = 1).futureValue
      result.summaries must contain only SubmissionSummary(item1)
      result.totalCount mustEqual 2
    }
  }

  "countByStatus" - {

    "must return the number of items for that particular status" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem.copy(status = SubmissionItemStatus.Failed)
      val item4 = randomItem.copy(status = SubmissionItemStatus.Forwarded)
      val item5 = randomItem.copy(status = SubmissionItemStatus.Completed)

      repository.countByStatus(SubmissionItemStatus.Submitted).futureValue mustEqual 0
      repository.countByStatus(SubmissionItemStatus.Failed).futureValue mustEqual 0
      repository.countByStatus(SubmissionItemStatus.Completed).futureValue mustEqual 0
      repository.countByStatus(SubmissionItemStatus.Forwarded).futureValue mustEqual 0

      List(item1, item2, item3, item4, item5)
        .traverse(repository.insert)
        .futureValue

      repository.countByStatus(SubmissionItemStatus.Submitted).futureValue mustEqual 2
      repository.countByStatus(SubmissionItemStatus.Failed).futureValue mustEqual 1
      repository.countByStatus(SubmissionItemStatus.Completed).futureValue mustEqual 1
      repository.countByStatus(SubmissionItemStatus.Forwarded).futureValue mustEqual 1
    }
  }

  "retry" - {

    "must reset the status of the given item to Submitted and set the reset counter to 1 when an item has no retries field" in {

      val item = randomItem.copy(status = SubmissionItemStatus.Forwarded)
      repository.insert(item).futureValue

      repository.retry(item.id).futureValue

      val updatedItem = repository.get(item.id).futureValue.value

      updatedItem.retries.value mustEqual 1
      updatedItem.status mustEqual SubmissionItemStatus.Submitted
    }

    "must reset the status of the given item to Submitted and increment the reset counter when an item has already been retried" in {

      val item = randomItem.copy(status = SubmissionItemStatus.Forwarded, retries = Some(1))
      repository.insert(item).futureValue

      repository.retry(item.id).futureValue

      val updatedItem = repository.get(item.id).futureValue.value

      updatedItem.retries.value mustEqual 2
      updatedItem.status mustEqual SubmissionItemStatus.Submitted
    }

    "must fail when there is no item with the given id" in {
      repository.retry(UUID.randomUUID().toString).failed.futureValue
    }
  }

  private def randomItem: SubmissionItem = item.copy(
    id = UUID.randomUUID().toString,
    sdesCorrelationId = UUID.randomUUID().toString,
    lastUpdated = clock.instant()
  )
}
