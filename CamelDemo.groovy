import com.google.common.base.Stopwatch
@Grab(group = "org.apache.camel", module = "camel-core", version = "2.0.0")
@Grab(group='joda-time', module='joda-time', version='2.9.4')
@Grab(group='com.google.guava', module='guava', version='19.0')

import org.apache.camel.ConsumerTemplate
import org.apache.camel.Endpoint
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

class QueueTemplateConsumer implements Runnable {
  private static final LocalLogger LOG = new LocalLogger(from: QueueTemplateConsumer.class)
//  private static final Random RND = new Random(System.currentTimeMillis());
  private static final Random RND = new Random(0L);

  private static final int MAX_WAIT_MILLIS = 5_000

  private int number
  private Endpoint endpoint
  private ConsumerTemplate template

  private CountDownLatch latch
  private Stopwatch timer

  @Override
  void run() {
    Object msg
    while (msg = template.receive(endpoint, MAX_WAIT_MILLIS)?.in?.body) {
      final int delay = 1_000 + (RND.nextInt(40) * 100) //Sleep between 1 and 5 seconds (random incremented by 10ths of a second

      LOG.log "${number}: Received: [${msg.toString()}]"
      Thread.sleep(delay)  //Sleep between 1 and 5 seconds (random incremented by 10ths of a second
      LOG.log "${number}: Finished processing for [${msg.toString()}]. Processing took ${delay/1_000} seconds"
      LOG.log "Processing took ${timer.elapsed(TimeUnit.MILLISECONDS)} milli-seconds so far"
    }
    LOG.log "Complete"
    latch?.countDown()
  }
}

final Stopwatch timer = new Stopwatch()
timer.start()

final int NUM_THREADS = 5
final int NUM_MESSAGES = 20

final String SEND_Q = "seda://foo"
final String RECV_Q = "seda://result"

final def logger = new LocalLogger(from: "main")

final def mrb = new MyRouteBuilder(from: SEND_Q, to: RECV_Q)
final def ctx = new DefaultCamelContext()
ctx.addRoutes mrb
ctx.start()

final CountDownLatch latch = new CountDownLatch(NUM_THREADS)
final def executor = Executors.newFixedThreadPool(NUM_THREADS)

final def ep = ctx.getEndpoint(RECV_Q)

//Using QueueTemplateConsumer
final def consumerTemplate = ctx.createConsumerTemplate()
(1..NUM_THREADS).forEach { i ->
  logger.log "Starting consumer ${i}"
  executor.execute(new QueueTemplateConsumer(number: i, endpoint: ep, template: consumerTemplate, latch: latch, timer: timer))
}

logger.log("Starting producer")
final def p = new QueueProducer(template: ctx.createProducerTemplate(), sendTo: SEND_Q.replaceAll("/", ""))
(1..NUM_MESSAGES).forEach { i ->
  p.send("Call ${i} to queue")
  Thread.sleep(500)
}

latch.await()

timer.stop()
logger.log "Finished. Took ${timer.elapsed(TimeUnit.MILLISECONDS)} milliseconds"
executor.shutdown()
