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

package models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class RelationshipDetailsSpec extends AnyFreeSpec with Matchers {

  val nino = "AA123456"

  ".hasChildBenefitRelationship" - {

    "must be true when there is an adult-child CBS relationship in the list" in {

      val details = RelationshipDetails(Relationships(Some(List(
        Relationship(nino, RelationshipType.AdultChild, RelationshipSource.CHB)
      ))))

      details.hasClaimedChildBenefit(nino) mustEqual true
    }

    "must be false when there are relationships, but claimant is not the parent in relationship" in {

      val details = RelationshipDetails(Relationships(Some(List(
        Relationship("JT123456", RelationshipType.AdultChild, RelationshipSource.CHB)
      ))))

      details.hasClaimedChildBenefit(nino) mustEqual false
    }

    "must be false when there are relationships, but not an adult-child CBS one" in {

      val details = RelationshipDetails(Relationships(Some(List(
        Relationship(nino, RelationshipType.AdultAdult, RelationshipSource.CHB),
        Relationship(nino, RelationshipType.AdultChild, RelationshipSource.TFC)
      ))))

      details.hasClaimedChildBenefit(nino) mustEqual false
    }

    "must be false when there are no relationships" in {

      val details = RelationshipDetails(Relationships(None))

      details.hasClaimedChildBenefit(nino) mustEqual false
    }
  }
}
