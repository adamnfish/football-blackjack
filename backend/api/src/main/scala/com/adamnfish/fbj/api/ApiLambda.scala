package com.adamnfish.fbj.api

import com.amazonaws.services.lambda.runtime.events.{
  APIGatewayV2HTTPEvent,
  APIGatewayV2HTTPResponse
}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}

import scala.jdk.CollectionConverters.*

/** Lambda handler behind the HTTP API's `POST /api/{operation}` proxy route.
  *
  * Walking-skeleton stub: accepts any event and answers 200 "ok". Grows into
  * `API.dispatch` with real persistence in phase 5.
  */
class ApiLambda
    extends RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse] {
  override def handleRequest(
      event: APIGatewayV2HTTPEvent,
      context: Context
  ): APIGatewayV2HTTPResponse =
    APIGatewayV2HTTPResponse
      .builder()
      .withStatusCode(200)
      .withHeaders(Map("content-type" -> "text/plain").asJava)
      .withBody("ok")
      .build()
}
