package com.adamnfish.fbj.devserver

/** Local webserver exposing the same API the Lambda serves in production, for
  * frontend development and the e2e suite (via the Vite proxy).
  *
  * Walking-skeleton stub: answers 200 "ok" to any POST /api/{operation},
  * matching the stub api Lambda. Grows API.dispatch, the /dev panel, and the
  * simulated clock through phases 2-4.
  */
object DevServer extends cask.MainRoutes {
  override def port: Int = 9090

  override def main(args: Array[String]): Unit = {
    super.main(args)
    println(s"Dev server listening on http://$host:$port")
  }

  @cask.post("/api/:operation")
  def api(operation: String): String =
    "ok"

  initialize()
}
