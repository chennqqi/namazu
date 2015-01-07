// Copyright (C) 2014 Nippon Telegraph and Telephone Corporation.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.osrg.earthquake;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import sun.util.logging.resources.logging_fr;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.*;
import java.net.*;

public class Inspector {
    private boolean Direct = false;
    private boolean Disabled = false;
    private String ProcessID;
    private int GATCPPort = 10000;

    private Logger LOGGER;

    private Socket GASock;
    private DataOutputStream GAOutstream;
    private DataInputStream GAInstream;

    private int SendReq(I2GMessage.I2GMsgReq req) {
        byte[] serialized = req.toByteArray();
        byte[] lengthBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(serialized.length).array();

        try {
            GAOutstream.write(lengthBuf);
            GAOutstream.write(serialized);
        } catch (IOException e) {
            return 1;
        }

        return 0;
    }

    private I2GMessage.I2GMsgRsp RecvRsp() {
        byte[] lengthBuf = new byte[4];

        try {
            GAInstream.read(lengthBuf, 0, 4);
        } catch (IOException e) {
            LOGGER.severe("failed to read header of response: " + e);
            return null;
        }

        int length = ByteBuffer.wrap(lengthBuf).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] rspBuf = new byte[length];

        try {
            GAInstream.read(rspBuf, 0, length);
        } catch (IOException e) {
            LOGGER.severe("failed to read body of response: " + e);
            return null;
        }

        I2GMessage.I2GMsgRsp rsp;
        try {
            rsp = I2GMessage.I2GMsgRsp.parseFrom(rspBuf);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.severe("failed to parse response: " + e);
            return null;
        }

        return rsp;
    }

    private I2GMessage.I2GMsgRsp ExecReq(I2GMessage.I2GMsgReq req) {
        int ret = SendReq(req);
        if (ret != 0) {
            LOGGER.severe("failed to send request");
            System.exit(1);
        }

        I2GMessage.I2GMsgRsp rsp = RecvRsp();
        if (rsp == null) {
            LOGGER.severe("failed to receive response");
            System.exit(1);
        }

        return rsp;
    }

    public Inspector() {
        LOGGER = Logger.getLogger(this.getClass().getName());
        LOGGER.setLevel(Level.INFO);

        try {
            FileHandler logFileHandler = new FileHandler("/tmp/earthquake-inspection-java.log");
            logFileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(logFileHandler);
        } catch (IOException e) {
            System.err.println("failed to initialize file hander for logging: " + e);
            System.exit(1);
        }

        String _Disabled = System.getenv("EQ_DISABLE");
        if (_Disabled != null) {
            LOGGER.info("inspection is disabled");
            return;
        }

        String _Direct = System.getenv("EQ_MODE_DIRECT");
        if (_Direct != null) {
            LOGGER.info("run in direct mode");
            Direct = true;
        } else {
            LOGGER.info("run in non direct mode");
        }

        ProcessID = System.getenv("EQ_ENV_PROCESS_ID");
        if (ProcessID == null) {
            LOGGER.severe("process id required but not given (EQ_ENV_PROCESS_ID");
            System.exit(1);
        }
        LOGGER.info("Process ID: " + ProcessID);

        String _GATCPPort = System.getenv("EQ_GA_TCP_PORT");
        if (_GATCPPort != null) {
            GATCPPort = Integer.parseInt(_GATCPPort);
            LOGGER.info("given TCP port of guest agent: " + GATCPPort);
        }
    }

    public void Initiation() {
        try {
            GASock = new Socket("localhost", GATCPPort);

            OutputStream out = GASock.getOutputStream();
            GAOutstream = new DataOutputStream(out);

            InputStream in = GASock.getInputStream();
            GAInstream = new DataInputStream(in);
        } catch (IOException e) {
            LOGGER.severe("failed to connect to guest agent: " + e);
            System.exit(1);
        }

        I2GMessage.I2GMsgReq_Initiation.Builder initiationReqBuilder = I2GMessage.I2GMsgReq_Initiation.newBuilder();
        I2GMessage.I2GMsgReq_Initiation initiationReq = initiationReqBuilder.setProcessId(ProcessID).build();

        I2GMessage.I2GMsgReq.Builder reqBuilder = I2GMessage.I2GMsgReq.newBuilder();
        I2GMessage.I2GMsgReq req = reqBuilder.setPid(0 /* FIXME */)
                                             .setTid((int)Thread.currentThread().getId())
                                             .setType(I2GMessage.I2GMsgReq.Type.INITIATION)
                                             .setMsgId(0)
                                             .setProcessId(ProcessID)
                                             .setInitiation(initiationReq).build();

        LOGGER.info("executing request for initiation");
        I2GMessage.I2GMsgRsp rsp = ExecReq(req);
        if (rsp.getRes() != I2GMessage.I2GMsgRsp.Result.ACK) {
            LOGGER.severe("initiation failed, result: " + rsp.getRes());
            System.exit(1);
        }

        LOGGER.info("initiation succeed");
    }
}