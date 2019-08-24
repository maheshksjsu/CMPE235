package net.dreamsw.homemon;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class NetActivity extends AppCompatActivity {
    public static final String OP = "op";
    public static final String src_ip = getIPAddress(true);
    public static final int src_port = 30602;
    private DatagramSocket socket;
    private String host = "52.15.130.208";
    private int port = 80;
    private boolean cleanup = false;
    private int delay = 500;
    private String oper;
    private DatagramPacket sendPacket;
    private DatagramPacket recvPacket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        //oper = getIntent().getParcelableExtra(OP);
        oper = getIntent().getStringExtra(OP);
        Log.d("TAG#", "function:" + oper);
        try {
            socket = new DatagramSocket(src_port);
            socket.setSoTimeout(120000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        final Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                Log.d("TAG#", "sending packet now");
                try {
                    Log.d("TAG#", "sending packet now");
                    recvPacket = new DatagramPacket(new byte[512], 512, InetAddress.getByName(host), port);
                    sendPacket = new DatagramPacket(new byte[512], 512, InetAddress.getByName(host), port);
                    //while (!cleanup)
                    {//cleanUp is set to true in onPause()
                        Log.d("TAG#", "sending packet now");
                        String str_buf = String.format("%s:%d", src_ip, src_port);
                        Log.d("TAG#", "str:"+str_buf);
                        byte[] buf = str_buf.getBytes();
                        sendPacket.setData(buf);
                        sendPacket.setLength(buf.length);
                        socket.send(sendPacket);
                        socket.receive(recvPacket);
                        String addr = recvPacket.getAddress().getHostAddress(); //private static field InetAddress addr
                        int port = recvPacket.getPort();
                        String data = new String(recvPacket.getData(), 0, recvPacket.getLength());
                        Log.d("TAG#", addr+":"+ port + "len" + recvPacket.getLength() + "data:" + data);
                        if (addr.equals(host)) {
                            Log.d("TAG#", "got address");
                            String[] destAddr = data.split(",");
                            String[] extAddr = destAddr[0].split(":");
                            String[] natAddr = destAddr[1].split(":");
                            String natHost = natAddr[0];
                            int natPort = Integer.parseInt(natAddr[1]);
                            String extHost = extAddr[0];
                            int extPort = Integer.parseInt(extAddr[1]);
                            String str_newbuf = new String("0");
                            byte[] newbuf = str_newbuf.getBytes();
                            sendPacket.setData(newbuf);
                            sendPacket.setLength(newbuf.length);

                            sendPacket.setAddress(InetAddress.getByName(natHost));
                            sendPacket.setPort(natPort);
                            socket.send(sendPacket);

                            sendPacket.setAddress(InetAddress.getByName(extHost));
                            sendPacket.setPort(extPort);
                            socket.send(sendPacket);

                            socket.setSoTimeout(2000);
                            socket.receive(recvPacket);
                            addr = recvPacket.getAddress().getHostAddress(); //private static field InetAddress addr
                            port = recvPacket.getPort();
                            String newdata = new String(recvPacket.getData(), 0, recvPacket.getLength());
                            Log.d("TAG#", "P2P recv:" + newdata);
                            if (addr.equals(natHost)) {
                                Log.d("TAG#", "using NAT Address:" + addr);
                            } else {
                                Log.d("TAG#", "using EXT Address:" + addr);
                            }

                            sendPacket.setAddress(InetAddress.getByName(addr));
                            sendPacket.setPort(port);
                            Log.d("TAG#", "doing action now");
                            do_function();

                        }
                    }
                } catch (Exception e) {
                    Log.d("TAG#", "timeout");
                    e.printStackTrace();
                } finally {
                    if (socket != null)
                        socket.close();
                    finish();
                }
            }

        });
        t.start();

    }

    private void send_data(String data) {
        byte[] buf = data.getBytes();
        sendPacket.setData(buf);
        sendPacket.setLength(buf.length);
        try {
            Log.d("TAG#", "sent data:" + data);
            String host = sendPacket.getAddress().getHostAddress();
            int port = sendPacket.getPort();
            Log.d("TAG#", "host:"+host+" port:" + port);
            socket.send(sendPacket);
        } catch (Exception e) {

        }
        return;
    }

    private String recv_data() {
        String recvData = "";
        try {
            socket.receive(recvPacket);
            String addr = recvPacket.getAddress().getHostAddress(); //private static field InetAddress addr
            int port = recvPacket.getPort();
            recvData = new String(recvPacket.getData(), 0, recvPacket.getLength());
            Log.d("TAG#", "P2P recv:" + recvData);
        } catch (Exception e) {

        }
        return recvData;
    }

    private void do_function() {
        Log.d("TAG#", "sending function");
        if (oper.equals("garage")) {
            Log.d("TAG#", "sending garage function");
            send_data("garage");
            String data = recv_data();
            if (data.equals("ok")) {
                Log.d("TAG#", "garage done");
            }
        } else {
            Log.d("TAG#", "function:" + oper + " not implemented yet");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanup =  true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        cleanup =  false;
    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { } // for now eat exceptions
        return "";
    }

}
//start both threads
