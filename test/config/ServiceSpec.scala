/*
 * Copyright 2024 HM Revenue & Customs
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

package config

import config.Service.convertToString
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper

class ServiceSpec
  extends AnyFreeSpec {

  private val service =
    Service(
      host     = "host",
      port     = "port",
      protocol = "protocol"
    )

  "Service.baseUrl" - {
    "must return protocol://host:port" in {
      service.baseUrl mustEqual "protocol://host:port"
    }
  }

  "convertToString" - {
    "must return protocol://host:port" in {
      convertToString(service) mustEqual "protocol://host:port"
    }
  }

  "override toString" - {
    "must return protocol://host:port" in {
      service.toString mustEqual "protocol://host:port"
    }
  }
}
