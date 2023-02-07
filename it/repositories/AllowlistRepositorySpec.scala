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

import models.{AllowlistEntry, Done}
import org.mockito.MockitoSugar
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration
import play.api.libs.json.{Json, __}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.security.SecureRandom
import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global

class AllowlistRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[AllowlistEntry]
    with OptionValues
    with ScalaFutures with MockitoSugar {

  private val aesKey = {
    val aesKey = new Array[Byte](32)
    new SecureRandom().nextBytes(aesKey)
    Base64.getEncoder.encodeToString(aesKey)
  }

  private val configuration = Configuration("crypto.key" -> aesKey)

  private implicit val crypto: Encrypter with Decrypter =
    SymmetricCryptoFactory.aesGcmCryptoFromConfig("crypto", configuration.underlying)

  protected override val repository = new AllowlistRepository(mongoComponent)

  private val entry1 = AllowlistEntry(SensitiveString("foo"))
  private val entry2 = AllowlistEntry(SensitiveString("bar"))

  ".get" - {

    "must return true when an entry exists in the database" in {

      insert(entry1).futureValue
      repository.exists(entry1).futureValue mustEqual true
    }

    "must return false when an entry does not exist in the database" in {

      insert(entry1).futureValue
      repository.exists(entry2).futureValue mustEqual false
    }
  }

  ".set" - {

    "must encrypt and write an entry to the repository" in {

      repository.set(entry1).futureValue mustEqual Done
      findAll().futureValue.head mustEqual entry1

      val rawBson = mongoComponent.database.getCollection[BsonDocument]("allowlist").find().head.futureValue
      val path = __ \ "_id"
      val parsed = path(Json.parse(rawBson.toJson))

      parsed match {
        case Seq(x) => x mustNot equal(entry1.nino.decryptedValue)
        case _ => ()
      }
    }

    "must return Done but not insert a new record when asked to insert a duplicate" in {

      repository.set(entry1).futureValue mustEqual Done
      repository.set(entry1).futureValue mustEqual Done

      findAll().futureValue.size mustEqual 1
    }
  }
}
