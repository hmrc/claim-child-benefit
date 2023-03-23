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

package models.relationship

import models.{Enumerable, WithName}

sealed trait RelationshipSource

object RelationshipSource extends Enumerable.Implicits {

  case object TFC extends WithName("TFC") with RelationshipSource
  case object EFE extends WithName("EFE") with RelationshipSource
  case object CHB extends WithName("CHB") with RelationshipSource

  val values: Seq[RelationshipSource] = Seq(TFC, EFE, CHB)

  implicit val enumerable: Enumerable[RelationshipSource] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
