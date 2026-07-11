package com.adamnfish.fbj.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent

import scala.jdk.CollectionConverters.*

class ApiLambdaTest extends munit.FunSuite {
  test("answers 200 ok to a POST /api/{operation} event") {
    val event = APIGatewayV2HTTPEvent
      .builder()
      .withRouteKey("POST /api/{operation}")
      .withPathParameters(Map("operation" -> "ping").asJava)
      .withBody("{}")
      .build()

    val response = new ApiLambda().handleRequest(event, null)

    assertEquals(response.getStatusCode, 200)
    assertEquals(response.getBody, "ok")
  }
}
