
# claim-child-benefit

This is the backend service for claim-child-benenfit-frontend. This service allows unauthenticated/authenticated users to claim Child Benefit by answering the relevant questions, printing out a PDF and posting it to HMRC to be processed. It replaces an existing iForm offering similar functionality. The service calls many different services:

- CBS to submit auntechicated users child benefit
- IF Individual details to get designatory details
- IF Relationship details to get relationship details
- Object Store and SDES to submit claim pdf files so case workers can view

More information is here - https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=673382904
 
### How to run the service
You can run the service using service manager with profile `CLAIM_CHILD_BENEFIT_ALL` `CLAIM_CHILD_BENEFIT` or locally with `sbt "run 11305"`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
