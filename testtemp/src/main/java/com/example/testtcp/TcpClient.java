package com.example.testtcp;

import android.content.Intent;
import android.util.Log;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Jason Zhu on 2017-04-25.
 */

public class TcpClient {
    private String TAG = "liyp_tcpclient";
    private String serverIP = "125.76.235.28";
    private int serverPort = 8088;
    private String serverUDPIP = "192.168.1.11";
    private int serverPort_self1 = 8001;
    private int serverPort_self2 = 8002;
    private int serverPort_can1 = 4001;

    private PrintWriter pw;
    private InputStream is;
    private DataInputStream dis;
    private boolean isRun = true;
    public static boolean isFlag = false;//发送UDP指令后
    private Socket socket = null;
    private byte buff[] = null;
    private int rcvLen;
    private OutputStream os;

    DatagramSocket datagramSocket = null;
    private static TcpClient tcpClient = null;
    ExecutorService exec = Executors.newCachedThreadPool();

    public TcpClient(String ip, int port) {
        this.serverIP = ip;
        this.serverPort = port;
    }

    private TcpClient() {
    }

    public static TcpClient getTcpClient() {
        if (tcpClient == null) {
            tcpClient = new TcpClient();
        }
        return tcpClient;
    }

    public static byte[] toBytes(String str) {
        if (str == null || str.trim().equals("")) {
            return new byte[0];
        }
        byte[] bytes = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }
        return bytes;
    }

    /**
     * 发送指令
     *
     * @param msg
     */
    public void sendTCPData(String msg) {
        getSocket(msg);
//        pw.println(toBytes(msg));
//        pw.flush();
//        if (pw.checkError()){
//            try {
//                pw = new PrintWriter(socket.getOutputStream(), true);
//                pw.println(toBytes(msg));
//                pw.flush();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
    }

    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String bytesToHexFun2(byte[] bytes) {
        char[] buf = new char[bytes.length * 2];
        int index = 0;
        for (byte b : bytes) {
            buf[index++] = HEX_CHAR[b >>> 4 & 0xf];
            buf[index++] = HEX_CHAR[b & 0xf];
        }
        return new String(buf);
    }

    /**
     * 发送UDP//udp
     */
    public void sendUDPData(final String data) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isFlag) {
                        new ReceiveThread().start();
                    }
                    datagramSocket = new DatagramSocket(serverPort_self1);
                    datagramSocket.setSoTimeout(3000);
                    InetAddress serverAddress = InetAddress.getByName(serverUDPIP);
                    byte[] bytes = toBytes(data);
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, serverAddress, serverPort_can1);
                    datagramSocket.send(packet);
                    Log.e("liyp_udp", "send msg data is:" + bytesToHexFun2(packet.getData()));
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    datagramSocket.close();
                    datagramSocket = null;
                }
            }
        };
        new Thread(runnable).start();
    }

    public class ReceiveThread extends Thread {

        @Override
        public void run() {
            super.run();
            isFlag = true;
            try {
                DatagramSocket receiveSocket = new DatagramSocket(serverPort_self2);
                while (!isInterrupted()) {
                    byte datas[] = new byte[512];
                    DatagramPacket packet = new DatagramPacket(datas, datas.length);
                    receiveSocket.receive(packet);
                    Log.e("liyp_udp", "receive msg data is:" + bytesToHexFun2(packet.getData()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            isFlag = false;
        }
    }

    /**
     * 接收
     */
    public void getSocket(final String cmd) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (socket == null || socket.isClosed()) {//TCP
                        try {
                            socket = new Socket(serverIP, serverPort);
                            Log.d(TAG, "isconnected =" + socket.isConnected());
                        } catch (ConnectTimeoutException e) {
                            Log.e(TAG, "socket 连接异常");
                            e.printStackTrace();
                        }
                    }
                    os = socket.getOutputStream();
                    if (os != null) {
                        os.write(toBytes(cmd));
                        os.flush();
                    }
                    Log.d(TAG, "isconnected =1111=" + socket.isConnected());
                    Log.d(TAG, "isClosed =1111=" + socket.isClosed());
                    // os = socket.getOutputStream();
                    //pw = new PrintWriter(socket.getOutputStream(), false);
                    // socket.shutdownOutput();
                    if (socket.isClosed()) {
                        Log.e(TAG, "Socket is closed  --------------------------");
                        return;
                    }
                    socket.setSoTimeout(3000);
                    is = socket.getInputStream();
                    dis = new DataInputStream(is);
                    while (isRun) {
                        try {
                            if (dis == null) return;
                            buff = new byte[128];
                            rcvLen = dis.read(buff);
                            if (rcvLen <= 0) continue;
                            // rcvMsg = new String(buff, 0, rcvLen, "utf-8");
                            Log.i(TAG, "run: 收到消息:" + bytesToHexFun2(buff));
                            Intent intent = new Intent();
                            intent.setAction("tcpClientReceiver");
                            intent.putExtra("tcpClientReceiver", bytesToHexFun2(buff));
                            FuncTcpClient.context.sendBroadcast(intent);//将消息发送给主界面
                            isRun = false;
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    isRun = false;
                    e.printStackTrace();
                } finally {
                    try {
                        isRun = true;
                        Log.e(TAG, "Socket is closed");
                        if (os != null) {
                            os.close();
                            os = null;
                        }
                        if (is != null) {
                            is.close();
                            is = null;
                        }
                        if (dis != null) {
                            dis.close();
                            dis = null;
                        }
                        if (socket != null) {
                            socket.close();
                            socket = null;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        exec.execute(runnable);
        //new Thread(runnable).start();
    }


}
