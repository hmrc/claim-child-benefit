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

package services

import connectors.IfConnector
import models.{Address, DesignatoryDetails, Name}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DesignatoryDetailsService @Inject() (
                                            connector: IfConnector
                                          )(implicit ec: ExecutionContext) {

  def getDesignatoryDetails(nino: String)(implicit hc: HeaderCarrier): Future[DesignatoryDetails] =
    connector.getDesignatoryDetails(nino).map { result =>

      val realName = result.names
        .filter(n => n.nameType == 1 && n.nameEndDate.isEmpty)
        .maxByOption(_.nameSequenceNumber)
        .map(Name(_))

      val knownAs = result.names
        .filter(n => n.nameType == 2 && n.nameEndDate.isEmpty)
        .maxByOption(_.nameSequenceNumber)
        .map(Name(_))

      val residentialAddress = result.addresses
        .filter(a => a.addressType == 1 && a.addressEndDate.isEmpty)
        .maxByOption(_.addressSequenceNumber)
        .map(Address(_))

      val correspondenceAddress = result.addresses
        .filter(a => a.addressType == 2 && a.addressEndDate.isEmpty)
        .maxByOption(_.addressSequenceNumber)
        .map(Address(_))

      DesignatoryDetails(
        realName = realName,
        knownAsName = knownAs,
        residentialAddress = residentialAddress,
        correspondenceAddress = correspondenceAddress
      )
    }
}
