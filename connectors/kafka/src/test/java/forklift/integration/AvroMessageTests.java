package forklift.integration;

import static org.junit.Assert.assertTrue;
import com.sofi.avro.schemas.StateCode;
import com.sofi.avro.schemas.UserRegistered;
import com.github.dcshock.avro.schemas.Address;
import com.github.dcshock.avro.schemas.ComplexAvroMessage;
import forklift.Forklift;
import forklift.connectors.ConnectorException;
import forklift.connectors.ForkliftMessage;
import forklift.consumer.Consumer;
import forklift.decorators.OnMessage;
import forklift.decorators.Producer;
import forklift.decorators.Properties;
import forklift.decorators.Queue;
import forklift.exception.StartupException;
import forklift.integration.server.TestServiceManager;
import forklift.producers.ForkliftProducerI;
import forklift.producers.ProducerException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by afrieze on 3/13/17.
 */
public class AvroMessageTests extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AvroMessageTests.class);
    private static boolean isInjectNull = true;
    TestServiceManager serviceManager;
    //    private static boolean ordered = true;
    private static boolean isPropsSet = false;

    @After
    public void after() {
        serviceManager.stop();
    }

    @Before
    public void setup() {
        serviceManager = new TestServiceManager();
        serviceManager.start();
        isInjectNull = true;
    }


    @Test
    public void testComplexAvroMessage() throws ProducerException, ConnectorException, InterruptedException, StartupException {
        Forklift forklift = serviceManager.newManagedForkliftInstance("");
        int msgCount = 10;
        ForkliftProducerI
                        producer =
                        forklift.getConnector().getQueueProducer("forklift-avro-topic");
        Map<String, String> producerProps = new HashMap<>();
        producerProps.put("Eye", "producerProperty");
        producer.setProperties(producerProps);
        for (int i = 0; i < msgCount; i++) {
            UserRegistered registered = new UserRegistered();
            registered.setFirstName("John");
            registered.setLastName("Doe");
            registered.setEmail("test@sofi.com");
            registered.setState(StateCode.MT);
            sentMessageIds.add(producer.send(registered));
        }
        final Consumer c = new Consumer(AvroMessageTests.RegisteredAvroConsumer.class, forklift);
        // Shutdown the consumer after all the messages have been processed.
        c.setOutOfMessages((listener) -> {
            listener.shutdown();
        });
        // Start the consumer.
        c.listen();
        messageAsserts();
    }


    @Queue("forklift-avro-topic")
    public static class RegisteredAvroConsumer {

        @forklift.decorators.Message
        private ForkliftMessage forkliftMessage;

        @forklift.decorators.Message
        private UserRegistered value;

        @forklift.decorators.Properties
        private Map<String, String> properties;

        @Producer(queue = "forklift-avro-topic")
        private ForkliftProducerI injectedProducer;

        @OnMessage
        public void onMessage() {
            if (value == null) {
                return;
            }
            System.out.println(Thread.currentThread().getName() + value.getState());
            consumedMessageIds.add(forkliftMessage.getId());
            isInjectNull = injectedProducer != null ? false : true;
        }
    }


    @Queue("forklift-avro-topic")
    public static class ForkliftAvroConsumer {

        @forklift.decorators.Message
        private ForkliftMessage forkliftMessage;
        
        @forklift.decorators.Message
        private ComplexAvroMessage value;

        @forklift.decorators.Properties
        private Map<String, String> properties;

        @Producer(queue = "forklift-avro-topic")
        private ForkliftProducerI injectedProducer;

        @OnMessage
        public void onMessage() {
            if (value == null) {
                return;
            }
            System.out.println(Thread.currentThread().getName() + value.getName() + value.getAddress().getCity());
            consumedMessageIds.add(forkliftMessage.getId());
            isInjectNull = injectedProducer != null ? false : true;
            isPropsSet = properties.get("Eye").equals("producerProperty");
        }
    }
}
