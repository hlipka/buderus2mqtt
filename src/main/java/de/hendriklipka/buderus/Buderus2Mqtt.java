package de.hendriklipka.buderus;

import de.hendriklipka.buderus.km200.KM200Comm;
import de.hendriklipka.buderus.km200.KM200Device;
import de.hendriklipka.buderus.km200.KM200ServiceTypes;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

@Command(name = "buderus2mqtt", mixinStandardHelpOptions = true, version = "buderus2mqtt 0.1",
        description = "Reads data from a Buderus KM100/200 and pushes it to Mqtt")
public class Buderus2Mqtt implements Runnable
{
    private static final Logger logger = LoggerFactory.getLogger(Buderus2Mqtt.class);

    @Option(names = "-l", description = "list all readable bindings and exit")
    boolean listOnly;

    @Parameters(paramLabel = "configuration file", arity = "1")
    String configFile;

    public static void main(String[] args)
    {
        CommandLine.run(new Buderus2Mqtt(), args);
    }

    public void run()
    {
        logger.info("Reading configuration file.");
        final Constructor constructor = new Constructor(Config.class);

        TypeDescription configDescription = new TypeDescription(Config.class);
        configDescription.addPropertyParameters("services", ServiceMapping.class);
        constructor.addTypeDescription(configDescription);
        configDescription.addPropertyParameters("mqttServers", MqttServerConfig.class);
        constructor.addTypeDescription(configDescription);

        Yaml yaml = new Yaml(constructor);

        InputStream is = null;
        try
        {
            is = new FileInputStream(configFile);
        }
        catch (FileNotFoundException e)
        {
            logger.error("Cannot open configuration file [{}]: {}", configFile, e.getMessage());
            System.exit(1);
        }

        Config config = yaml.load(is);

        logger.info("Connecting to MQTT servers.");

        String tempDir = System.getProperty("java.io.tmpdir");
        File temp = new File(tempDir, "mqtt");
        if (!temp.mkdir() && !temp.exists())
        {
            logger.error("Cannot create temporary folder [{}] for MQTT locking.", temp.getAbsolutePath());
            System.exit(3);
        }

        List<IMqttClient> mqttClients = new ArrayList<>();
        for (MqttServerConfig serverConfig: config.getMqttServers())
        {
            final String connectURI = serverConfig.isSecure()?"ssl":"tcp"+"://" + serverConfig.getServer() + ":" + serverConfig.getPort();
            IMqttClient mqttClient = null;
            try
            {
                MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence(temp.getAbsolutePath());
                mqttClient = new MqttClient(connectURI, serverConfig.getClientId(), persistence);
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);
                if (StringUtils.isNotBlank(serverConfig.getUser()))
                {
                    options.setUserName(serverConfig.getUser());
                }
                if (StringUtils.isNotBlank(serverConfig.getPassword()))
                {
                    options.setUserName(serverConfig.getPassword());
                }
                mqttClient.connect(options);
            }
            catch (MqttException e)
            {
                logger.error("cannot connect to MQTT server [{}]: {}", connectURI, e.getMessage());
                System.exit(3);
            }
            mqttClients.add(mqttClient);
        }

        logger.info("Connect to Buderus device.");
        KM200Device device = new KM200Device();
        device.setIP4Address(config.getBuderusServer());

        if (StringUtils.isNotBlank(config.getPrivateKey()))
        {
            device.setCryptKeyPriv(config.getPrivateKey());
        }
        else
        {
            device.setGatewayPassword(config.getGatewayPassword());
            device.setPrivatePassword(config.getPrivatePassword());
            device.setMD5Salt(config.getMd5Salt());
        }

        KM200Comm comm = new KM200Comm();
        if (!initDevice(device, comm))
        {
            logger.error("Cannot connect to Buderus device at [{}]. Please check configuration.", config.getBuderusServer());
            System.exit(2);
        }
        logger.info("Connected. Retrieve complete list of services.");

        for (KM200ServiceTypes service : KM200ServiceTypes.values())
        {
            comm.initObjects(device, service.getDescription());
        }
        if (listOnly)
        {
            logger.info("Listing all known and readable services.");
            device.listAllServices();
            System.err.println("List of all services written to the log file. Exiting.");
            System.exit(0);
        }

        logger.info("Retrieval of the KM200 service information completed.");
        device.setInited(true);

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        final Runnable runnableTask = new MqttPublisher(config.getServices(), device, comm, mqttClients);
        executorService.scheduleAtFixedRate(runnableTask, 1, config.getIntervalSeconds(), TimeUnit.SECONDS);
    }

    private boolean initDevice(final KM200Device device, final KM200Comm comm)
    {
        byte[] recData = comm.getDataFromService(device, "/gateway/DateTime");
        if (recData == null)
        {
            logger.error("Communication to Buderus device is not possible, no data received!");
            return false;
        }
        if (recData.length == 0)
        {
            logger.error("No reply from KM200!");
            return false;
        }

        /* Decrypt the message */
        String decodedData = comm.decodeMessage(device, recData);
        if (decodedData == null)
        {
            logger.error("Decoding of the KM200 message is not possible!");
            return false;
        }

        if (decodedData.equals("SERVICE NOT AVAILABLE"))
        {
            logger.error("Error while talking to Buderus device: retrieval of '/gateway/DateTime' returns 'SERVICE NOT AVAILABLE'.");
            return false;
        }
        logger.info("Communication test to the Buderus device was successful.");
        return true;
    }

    private static class MqttPublisher implements Runnable
    {

        private final List<ServiceMapping> _mappings;
        private final KM200Device _device;
        private final KM200Comm _comm;
        private final List<IMqttClient> mqttClients;

        private MqttPublisher(final List<ServiceMapping> mappings, final KM200Device device, final KM200Comm comm, final List<IMqttClient> clients)
        {
            _mappings = mappings;
            _device = device;
            _comm = comm;
            mqttClients = clients;
        }

        @Override
        public void run()
        {
            logger.debug("Starting MQTT publishing for configured services.");
            for (ServiceMapping service : _mappings)
            {
                if (service.getType().equals("float"))
                {
                    Double d = getDoubleValue(_device, _comm, service.getServiceName());
                    if (null==d)
                    {
                        logger.error("Could not get value for service "+ service.getServiceName()+", skipping.");
                        continue;
                    }
                    MessageParameters params = new MessageParameters(service.getServiceName(), ServiceType.valueOf(service.getType().toUpperCase(Locale.US)), d);
                    String messageStr = params.replace(service.getMqttMessage());

                    MqttMessage message = new MqttMessage(messageStr.getBytes());
                    message.setQos(service.getQos());
                    message.setRetained(service.isRetained());
                    final String fullTopic = service.getMqttTopic();
                    final String finalTopic = params.replace(fullTopic);

                    logger.debug("Publish MQTT message '{}'->'{}'", finalTopic, message.toString());

                    try
                    {
                        for (IMqttClient client : mqttClients)
                        {
                            if (!client.isConnected())
                            {
                                try
                                {
                                    client.reconnect();
                                    if (!client.isConnected())
                                    {
                                        logger.error("Could not reconnect to MQTT server " + client.getServerURI());
                                        continue;
                                    }
                                }
                                catch (MqttException e)
                                {
                                    logger.error("Error while reconnecting to MQTT server "+client.getServerURI()+" : ", e);
                                    continue;
                                }
                            }
                            client.publish(finalTopic, message);
                        }
                    }
                    catch (MqttException e)
                    {
                        logger.error("Cannot send MQTT message, stopping publishing: {}", e.getMessage());
                        return;
                    }

                }
                else
                {
                    logger.warn("Unknown service type [{}], ignoring service.", service.getType());
                }
            }
        }

        private Double getDoubleValue(final KM200Device device, final KM200Comm comm, final String service)
        {
            final byte[] data = comm.getDataFromService(device, service);
            if (null==data)
            {
                logger.error("Did not receive any data from KM200 device for service {}", service);
                return null;
            }
            String s = comm.decodeMessage(device, data);
            JSONObject nodeRoot = new JSONObject(s);
            return nodeRoot.getDouble("value");
        }
    }
}
