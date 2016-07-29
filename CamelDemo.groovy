@Grab(group = "org.apache.camel", module = "camel-core", version = "2.0.0")
@Grab(group='joda-time', module='joda-time', version='2.9.4')
@Grab(group='com.google.guava', module='guava', version='19.0')

import com.google.common.base.Stopwatch

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.builder.RouteBuilder

import org.joda.time.DateTime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MyRouteBuilder extends RouteBuilder {
  private def from
  private def to

  void configure() {
    from(from).to(to)
  }
}

class LocalLogger {
  private def from

  def log(def msg) {
    println "${new DateTime().toString()}\t${from.toString()}\t[Thread: ${Thread.currentThread().getName()}]\t- ${msg}"
  }
}

class QueueProducer {
  private static final LocalLogger LOG = new LocalLogger(from: QueueProducer.class)

  private def template
  private String sendTo

  def send(def msg) {
    LOG.log "Sending: [${msg}]"
    template.sendBody(sendTo, msg)
  }
}

class QueueProcessor implements Processor {
  private static final LocalLogger LOG = new LocalLogger(from: QueueProcessor.class)
//  private static final Random RND = new Random(System.currentTimeMillis());
  private static final Random RND = new Random(0L);

  private Stopwatch timer

  @Override
  void process(final Exchange ex) throws Exception {
    final int delay = 1_000 + (RND.nextInt(40) * 100)   //Sleep between 1 and 5 seconds (random incremented by 10ths of a second

    LOG.log "Received: [${ex.toString()}]"
    Thread.sleep(delay)
    LOG.log "Finished processing for [${ex.toString()}]. Processing took ${delay/1_000} seconds"
    LOG.log "Processing took ${timer.elapsed(TimeUnit.MILLISECONDS)} milli-seconds so far"
  }
}

final Stopwatch timer = new Stopwatch()
timer.start()

final int NUM_MESSAGES = 20

final String SEND_Q = "seda://foo"
final String RECV_Q = "seda://result"

final def logger = new LocalLogger(from: "main")

final def mrb = new MyRouteBuilder(from: SEND_Q, to: RECV_Q)
final def ctx = new DefaultCamelContext()
ctx.addRoutes mrb
ctx.start()

final def ep = ctx.getEndpoint(RECV_Q)

final def cons = ep.createConsumer(new QueueProcessor(timer: timer))
cons.start()

logger.log("Starting producer")
final def p = new QueueProducer(template: ctx.createProducerTemplate(), sendTo: SEND_Q.replaceAll("/", ""))
(1..NUM_MESSAGES).forEach { i ->
  p.send("Call ${i} to queue")
  Thread.sleep(500)
}


Thread.sleep(60_000)

timer.stop()
logger.log "Finished. Took ${timer.elapsed(TimeUnit.MILLISECONDS)} milliseconds"
