/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package de.hendriklipka.buderus.km200;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

/**
 * This class was taken from the OpenHAB 1.x Buderus / KM200 binding, and modified to run without the OpenHAB infrastructure. Not needed code was removed.
 * The KM200Comm class does the communication to the device and does any encryption/decryption/converting jobs
 *
 * @author Markus Eckhardt
 * @since 1.9.0
 */

public class KM200Comm
{

    private static final Logger logger = LoggerFactory.getLogger(KM200Comm.class);
    private boolean _connected = false;

    final PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setConnectionConfigResolver(route -> ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(30))
                    .setSocketTimeout(Timeout.ofSeconds(30))
                    .setValidateAfterInactivity(TimeValue.ofSeconds(15))
                    .setTimeToLive(TimeValue.ofMinutes(1))
                    .build())
            .build();

    public KM200Comm()
    {
    }

    /**
     * This function removes zero padding from a byte array.
     */
    public static byte[] removeZeroPadding(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0)
        {
            --i;
        }
        return Arrays.copyOf(bytes, i + 1);
    }

    /**
     * This function does the GET http communication to the device
     */
    public byte[] getDataFromService(KM200Device device, String service)
    {
        byte[] responseBodyB64 = null;
        // Create an instance of HttpClient.
        try (CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(3, TimeValue.ofSeconds(10)))
                .build())
        {

            // Create a method instance.
            HttpGet method = new HttpGet("http://" + device.getIP4Address() + service);


            CloseableHttpResponse response = null;
            try
            {
                URI uri = new URIBuilder(method.getUri())
                        .addParameter("param1", "value1")
                        .addParameter("param2", "value2")
                        .build();
                method.setUri(uri);
                // Set the right header
                method.setHeader("Accept", "application/json");
                method.addHeader("User-Agent", "TeleHeater/2.2.3");

                // Execute the method.
                response = client.execute(method);
                int statusCode = response.getCode();
                // Check the status and the forbidden 403 Error.
                if (statusCode != HttpStatus.SC_OK)
                {
                    String statusLine = response.getReasonPhrase();
                    if (statusLine.contains(" 403 "))
                    {
                        return new byte[1];
                    }
                    else
                    {
                        logger.error("HTTP GET failed: {}", response.getReasonPhrase());
                        return null;
                    }
                }
                final HttpEntity entity = response.getEntity();
                String contentTypeStr = response.getFirstHeader("Content-type").getValue();
                if (StringUtils.isNotEmpty(contentTypeStr))
                {
                    ContentType contentType = ContentType.parse(contentTypeStr);
                    device.setCharSet(contentType.getCharset().name());
                }
                // Read the response body.
                responseBodyB64 = ByteStreams.toByteArray(entity.getContent());

            }
            catch (IOException e)
            {
                logger.error("Fatal transport error: ", e);
                _connected = false;
            }
            catch (URISyntaxException e)
            {
                logger.error("Error building connect URI: ", e);
                _connected = false;
            }
            finally
            {
                // Release the connection.
                try
                {
                    if (null!=response)
                    {
                        response.close();
                    }
                }
                catch (IOException e)
                {
                    logger.error("Error while closing connection to Buderus server: ", e);
                }
            }
        }
        catch (IOException e)
        {
            logger.error("Error while creating HTTP client: e");
        }
        return responseBodyB64;
    }

    /**
     * This function does the decoding for a new message from the device
     */
    public String decodeMessage(KM200Device device, byte[] encoded)
    {
        String retString;
        byte[] decodedB64;

        try
        {
            decodedB64 = Base64.decodeBase64(encoded);
        }
        catch (Exception e)
        {
            logger.error("Message is not in valid Base64 scheme: ", e);
            e.printStackTrace();
            return null;
        }
        try
        {
            /* Check whether the length of the decryptData is NOT multiplies of 16 */
            if ((decodedB64.length & 0xF) != 0)
            {
                /* Return the data */
                retString = new String(decodedB64, device.getCharSet());
                return retString;
            }
            // --- create cipher
            final Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(device.getCryptKeyPriv(), "AES"));
            final byte[] decryptedData = cipher.doFinal(decodedB64);
            byte[] decryptedDataWOZP = removeZeroPadding(decryptedData);
            retString = new String(decryptedDataWOZP, device.getCharSet());
            return retString;
        }
        catch (BadPaddingException | IllegalBlockSizeException | UnsupportedEncodingException
                | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e)
        {
            // failure to authenticate
            logger.error("Exception on encoding: ", e);
            return null;
        }
    }

    /**
     * This function checks the capabilities of a service on the device
     */
    public void initObjects(KM200Device device, String service)
    {
        String id, type, decodedData = null;
        int writeable = 0;
        int recordable = 0;
        JSONObject nodeRoot;
        KM200CommObject newObject;
        logger.debug("Init: {}", service);
        if (device.blacklistMap.contains(service))
        {
            logger.debug("Service on blacklist: {}", service);
            return;
        }
        byte[] recData = getDataFromService(device, service);
        try
        {
            if (recData == null)
            {
                throw new RuntimeException("Communication is not possible!");
            }
            if (recData.length == 0)
            {
                throw new RuntimeException("No reply from KM200!");
            }
            /* Look whether the communication was forbidden */
            if (recData.length == 1)
            {
                newObject = new KM200CommObject(service, "", 0, 0, 0);
                device.serviceMap.put(service, newObject);
                return;
            }
            decodedData = decodeMessage(device, recData);
            if (decodedData == null)
            {
                throw new RuntimeException("Decoding of the KM200 message is not possible!");
            }
            if (decodedData.length() > 0)
            {
                nodeRoot = new JSONObject(decodedData);
                type = nodeRoot.getString("type");
                id = nodeRoot.getString("id");
            }
            else
            {
                logger.error("Get empty reply");
                return;
            }

            /* Check the service features and set the flags */
            if (nodeRoot.has("writeable"))
            {
                int val = nodeRoot.getInt("writeable");
                logger.debug(Integer.toString(val));
                writeable = val;
            }
            if (nodeRoot.has("recordable"))
            {
                int val = nodeRoot.getInt("recordable");
                logger.debug(Integer.toString(val));
                recordable = val;
            }
            logger.debug("Typ: {}", type);

            newObject = new KM200CommObject(id, type, writeable, recordable);

            /* Check whether the type is a single value containing a string value */
            switch (type)
            {
                case "stringValue":
                {
                    Object valObject;
                    logger.debug("initDevice: type string value: {}", decodedData);
                    valObject = nodeRoot.getString("value");
                    newObject.setValue(valObject);
                    if (nodeRoot.has("allowedValues"))
                    {
                        List<String> valParas = new ArrayList<>();
                        JSONArray paras = nodeRoot.getJSONArray("allowedValues");
                        for (int i = 0; i < paras.length(); i++)
                        {
                            String subJSON = (String) paras.get(i);
                            valParas.add(subJSON);
                        }
                        newObject.setValueParameter(valParas);
                    }
                    device.serviceMap.put(id, newObject);

                    break;
                }
                case "floatValue":
                { /* Check whether the type is a single value containing a float value */
                    Object valObject;
                    logger.debug("initDevice: type float value: {}", decodedData);
                    valObject = (float) nodeRoot.getDouble("value");
                    newObject.setValue(valObject);
                    if (nodeRoot.has("minValue") && nodeRoot.has("maxValue"))
                    {
                        List<Float> valParas = new ArrayList<>();
                        valParas.add((float) nodeRoot.getDouble("minValue"));
                        valParas.add((float) nodeRoot.getDouble("maxValue"));
                        newObject.setValueParameter(valParas);
                    }
                    device.serviceMap.put(id, newObject);

                    break;
                }
                case "switchProgram":  /* Check whether the type is a switchProgram */
                    logger.debug("initDevice: type switchProgram {}", decodedData);
                    newObject.setValue(decodedData);
                    device.serviceMap.put(id, newObject);
                    /* have to be completed */

                    break;
                case "errorList":  /* Check whether the type is a errorList */
                    logger.debug("initDevice: type errorList: {}", decodedData);
                    JSONArray errorValues = nodeRoot.getJSONArray("values");
                    newObject.setValue(errorValues);
                    /* have to be completed */

                    break;
                case "refEnum":  /* Check whether the type is a refEnum */
                    logger.debug("initDevice: type refEnum: {}", decodedData);
                    device.serviceMap.put(id, newObject);
                    JSONArray refers = nodeRoot.getJSONArray("references");
                    for (int i = 0; i < refers.length(); i++)
                    {
                        JSONObject subJSON = refers.getJSONObject(i);
                        id = subJSON.getString("id");
                        initObjects(device, id);
                    }

                    break;
                case "moduleList":  /* Check whether the type is a moduleList */
                    logger.debug("initDevice: type moduleList: {}", decodedData);
                    device.serviceMap.put(id, newObject);
                    JSONArray vals = nodeRoot.getJSONArray("values");
                    for (int i = 0; i < vals.length(); i++)
                    {
                        JSONObject subJSON = vals.getJSONObject(i);
                        id = subJSON.getString("id");
                        initObjects(device, id);
                    }

                    break;
                case "yRecording":  /* Check whether the type is a yRecording */
                    logger.debug("initDevice: type yRecording: {}", decodedData);
                    device.serviceMap.put(id, newObject);
                    /* have to be completed */

                    break;
                case "systeminfo":  /* Check whether the type is a systeminfo */
                    logger.debug("initDevice: type systeminfo: {}", decodedData);
                    JSONArray sInfo = nodeRoot.getJSONArray("values");
                    newObject.setValue(sInfo);
                    device.serviceMap.put(id, newObject);
                    /* have to be completed */

                    break;
                default:  /* Unknown type */
                    logger.info("initDevice: type unknown for service: {}",
                            service + "Data:" + decodedData);
                    newObject.setValue(decodedData);
                    device.serviceMap.put(id, newObject);
                    break;
            }
        }
        catch (

                JSONException e)
        {
            logger.error("Parsingexception in JSON: {} data: {}", e, decodedData);
            e.printStackTrace();
        }
    }

    public void connect(final KM200Device device)
    {
        byte[] recData = getDataFromService(device, "/gateway/DateTime");
        if (recData == null)
        {
            logger.error("Communication to Buderus device is not possible, no data received!");
            return;
        }
        if (recData.length == 0)
        {
            logger.error("No reply from KM200!");
            return;
        }

        /* Decrypt the message */
        String decodedData = decodeMessage(device, recData);
        if (decodedData == null)
        {
            logger.error("Decoding of the KM200 message is not possible!");
            return;
        }

        if (decodedData.equals("SERVICE NOT AVAILABLE"))
        {
            logger.error("Error while talking to Buderus device: retrieval of '/gateway/DateTime' returns 'SERVICE NOT AVAILABLE'.");
            return;
        }
        logger.info("Communication test to the Buderus device was successful.");
        _connected = true;
    }

    public boolean isConnected()
    {
        return _connected;
    }
}