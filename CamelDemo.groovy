@Grab(group = "org.apache.camel", module = "camel-core", version = "2.0.0")
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.builder.RouteBuilder

class MyRouteBuilder extends RouteBuilder {
  void configure() {
      from("direct://foo").to("mock://result")
  }
}

mrb = new MyRouteBuilder()
ctx = new DefaultCamelContext()
ctx.addRoutes mrb
ctx.start()

p = ctx.createProducerTemplate()
p.sendBody "direct:foo", "Camel Ride for beginner"

e = ctx.getEndpoint("mock://result")
ex = e.exchanges.first()
println "INFO> ${ex}"
