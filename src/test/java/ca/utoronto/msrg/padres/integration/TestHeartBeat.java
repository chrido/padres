package ca.utoronto.msrg.padres.integration;

import ca.utoronto.msrg.padres.AllTests;
import ca.utoronto.msrg.padres.MessageWatchAppender;
import ca.utoronto.msrg.padres.PatternFilter;
import org.junit.*;

import ca.utoronto.msrg.padres.broker.brokercore.BrokerConfig;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCore;
import ca.utoronto.msrg.padres.broker.brokercore.BrokerCoreException;
import ca.utoronto.msrg.padres.broker.brokercore.InputQueueHandler;
import ca.utoronto.msrg.padres.common.message.MessageType;
import ca.utoronto.msrg.padres.common.message.Publication;
import ca.utoronto.msrg.padres.common.message.parser.MessageFactory;
import ca.utoronto.msrg.padres.common.message.parser.ParseException;
import ca.utoronto.msrg.padres.common.util.LogSetup;
import ca.utoronto.msrg.padres.integration.tester.GenericBrokerTester;
import ca.utoronto.msrg.padres.integration.tester.TesterBrokerCore;
import ca.utoronto.msrg.padres.integration.tester.TesterMessagePredicates;
import ca.utoronto.msrg.padres.tools.padresmonitor.MonitorFrame;
import ca.utoronto.msrg.padres.tools.padresmonitor.OverlayManager;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static ca.utoronto.msrg.padres.AllTests.setupConfigurations;

/**
 * This class provides a way to test HeartBeat functionality. In order to run this class,
 * rmiregistry 1099 and 1100 need to be done first.
 *
 * @author Shuang Hou
 */
@RunWith(Parameterized.class) //TODO: Currently ignored because it requires a X11 Display, run manually
public class TestHeartBeat extends Assert {

    @Parameterized.Parameter(value = 0)
    public int configuration;

    @Parameterized.Parameter(value = 1)
    public String method;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { 1, "socket" }, { 1, "rmi" },
                { 2, "socket" }, { 2, "rmi" },
                { 3, "socket" }, { 3, "rmi" },
                { 4, "socket" }, { 4, "rmi" }
        });
    }

    protected GenericBrokerTester _brokerTester;

    protected BrokerCore brokerCore1;

    protected BrokerCore brokerCore2;

    protected MonitorFrame padresMonitor;

    protected MessageWatchAppender messageWatcher;

    protected PatternFilter msgFilter;

    @Before
    public void setUp() throws Exception {
        setupConfigurations(this.configuration, this.method);
        AllTests.setupStarNetwork01();

        _brokerTester = new GenericBrokerTester();

        // start the broker
        brokerCore1 = createNewBrokerCore(AllTests.brokerConfig01);
        brokerCore1.initialize();
        brokerCore2 = createNewBrokerCore(AllTests.brokerConfig02);
        brokerCore2.initialize();
        // start monitor for broker1
        padresMonitor = new MonitorFrame();
        padresMonitor.getOverlayManager().connect(brokerCore1.getBrokerURI());
        padresMonitor.getOverlayManager().setHeartbeatParameters(brokerCore1.getBrokerID(), true,
                3000, 5000, 2);
        // setup filters and message watcher
        messageWatcher = new MessageWatchAppender();
        msgFilter = new PatternFilter(InputQueueHandler.class.getName());
        msgFilter.setPattern("");
        messageWatcher.addFilter(msgFilter);
        LogSetup.addAppender("MessagePath", messageWatcher);
    }

    protected BrokerCore createNewBrokerCore(BrokerConfig brokerConfig) throws BrokerCoreException {
        return new TesterBrokerCore(_brokerTester, brokerConfig);
    }

    @After
    public void tearDown() throws Exception {

        try{
            padresMonitor.shutdown();
        }catch (Exception ex)
        {
            System.out.println("TODO: this should be started properly in the first place, get rid of user interface to run it headless");
            ex.printStackTrace();
        }
        brokerCore1.shutdown();
        brokerCore2.shutdown();
        LogSetup.removeAppender("MessagePath", messageWatcher);
        padresMonitor = null;

        brokerCore1 = null;
        brokerCore2 = null;
        _brokerTester = null;
    }

    /**
     * Test the HeartBeat ACK publication is sent back to the source broker, where broker1.HeartBeat
     * is ON, and broker2.HeartBeat is OFF. broker1 sends heartBeat REQ to broker2, and broker2
     * sends heartBeat ACK to broker1.
     *
     * @throws ParseException
     * @throws InterruptedException
     */
    @Test
    @Ignore
    public void testHeartBeatACK() throws ParseException, InterruptedException {
        /* TODO: REZA (DONE2) */
        _brokerTester.clearAll().
                expectReceipt(
                        brokerCore1.getBrokerURI(),
                        MessageType.PUBLICATION,
                        new TesterMessagePredicates().
                                addPredicate("class", "eq", "HEARTBEAT_MANAGER").
                                addPredicate("brokerID", "eq", brokerCore1.getBrokerID()).
                                addPredicate("type", "eq", "HEARTBEAT_ACK").
                                addPredicate("fromID", "eq", brokerCore2.getBrokerID()),
                        "INPUTQUEUE").
                expectSend(
                        brokerCore2.getBrokerURI(),
                        MessageType.PUBLICATION,
                        new TesterMessagePredicates().
                                addPredicate("class", "eq", "HEARTBEAT_MANAGER").
                                addPredicate("brokerID", "eq", brokerCore1.getBrokerID()).
                                addPredicate("type", "eq", "HEARTBEAT_ACK").
                                addPredicate("fromID", "eq", brokerCore2.getBrokerID()),
                        brokerCore1.getBrokerURI());
        assertTrue(_brokerTester.waitUntilExpectedEventsHappen());
    }

    /**
     * Test the HeartBeat failureDetect, where broker1.HeartBeat is ON, and broker2.HeartBeat is
     * OFF. broker2 is stoped, then broker1 sends failureDetect publication to monitor. The
     * failureDetect can be shown on the monitor. Then broker2 is resumed, and broker1 sends
     * failureCleared publication to monitor.
     *
     * @throws ParseException
     * @throws InterruptedException
     */
    @Test
    @Ignore
    public void testHeartBeatFailureDetectAndFailureCleared() throws ParseException, InterruptedException {
        // setup the message filter
        msgFilter = new PatternFilter(OverlayManager.class.getName());
        msgFilter.setPattern(".*Publication.+HEARTBEAT_MANAGER.+FAILURE_DETECTED.+");
        messageWatcher.clearFilters();
        messageWatcher.addFilter(msgFilter);
        // issue command to stop broker
        padresMonitor.getOverlayManager().stopBroker(brokerCore2.getBrokerID());
        // waiting for routing finished
        String msg = messageWatcher.getMessage(20);

        Publication expectedPub = MessageFactory.createPublicationFromString("[class,HEARTBEAT_MANAGER],[detectedID,'"
                + brokerCore2.getBrokerID() + "']," + "[type,'FAILURE_DETECTED'],[detectorID,'"
                + brokerCore1.getBrokerID() + "']");
        String pubString = expectedPub.toString().split(";")[0];
        assertTrue("The failureDetectPub should be sent from Broker1 to Monitor",
                msg.contains(pubString));

        // setup the message filter
        msgFilter.setPattern(".*Publication.+HEARTBEAT_MANAGER.+FAILURE_CLEARED.+");
        // issue command to resume broker
        padresMonitor.getOverlayManager().resumeBroker(brokerCore2.getBrokerID());
        // waiting for routing finished
        msg = messageWatcher.getMessage(20);

        expectedPub = MessageFactory.createPublicationFromString("[class,HEARTBEAT_MANAGER],[detectedID,'"
                + brokerCore2.getBrokerID() + "']," + "[type,'FAILURE_CLEARED'],[detectorID,'"
                + brokerCore1.getBrokerID() + "']");
        pubString = expectedPub.toString().split(";")[0];
        assertTrue("The failureClearedPub should be sent from Broker1 to Monitor",
                msg.contains(pubString));
    }
}
