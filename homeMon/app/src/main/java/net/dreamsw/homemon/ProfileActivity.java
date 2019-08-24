package net.dreamsw.homemon;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Random;


import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProfileActivity extends AppCompatActivity {
    public static int count =0;
    public static String msg = "";
    byte[] bytes = new byte[0];
    public static  Lock lck = new ReentrantLock();
    public Socket peer_socket = null;
    public static final String GOOGLE_ACCOUNT = "google_account";
    //public static final String FUNCTION = "function";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private TextView profileName;
    private TextView profileEmail;
    private ImageView profileImage;
    private Button signOut;
    private Button buttonGarage;
    private Button buttonSprinkler;
    private Button buttonExit;
    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount googleSignInAccount;
    private String name;
    private String email;
    private Thread mainThread;
    private Socket mainSocket;
    private String src_pub_ip;
    private int src_pub_port;
    private String src_priv_ip;
    private int src_priv_port;
    private String peer_pub_ip;
    private int peer_pub_port;
    private String peer_priv_ip;
    private int peer_priv_port;
    private String peer_ip;
    private int peer_port;
    ImageView garageView;
    File img_file;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        signOut=findViewById(R.id.sign_out);
        signOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                googleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        //On Succesfull signout we navigate the user back to LoginActivity
                        Intent intent=new Intent(ProfileActivity.this,MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
            }
        });
        setDataOnView();
    }


    private void setDataOnView() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.serverClientId))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInAccount = getIntent().getParcelableExtra(GOOGLE_ACCOUNT);
        String token = googleSignInAccount.getIdToken();
        Log.d("TAG#", "token2:" + token);
        buttonGarage=findViewById(R.id.buttonGarage);

        buttonGarage=findViewById(R.id.buttonGarage);
        buttonGarage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("TAG#", "set button");
                buttonGarage.setBackgroundColor(Color.DKGRAY);
                Log.d("TAG#", "setting data");
                String enc_msg = AESCipher.encrypt("garage_op");
                Log.d("TAG#", "mahesh:msg:"+enc_msg);
                lck.lock();
                msg = enc_msg;
                lck.unlock();
                Log.d("TAG#", "set data");
                TimerTask task  = new TimerTask() {
                    @Override
                    public void run() {
                        Log.d("TAG#", "restoring button");
                        buttonGarage.setBackgroundColor(0xffff8800);
                    }
                };
                new Timer().schedule(task, 300);
            }
        });


        buttonExit =findViewById(R.id.buttonExit);
        buttonExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("TAG#", "exiting...");
                try {
                    peer_socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finish();
                System.exit(0);
            }
        });
        /*
         * start the main thread for TCP hole punching
         */
        this.mainThread = new Thread(new MainThread());
        this.mainThread.start();
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getDir("", Context.MODE_PRIVATE);
        img_file = new File(directory, "garage.jpg");
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            mainSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void do_garage() throws IOException {
        String token = googleSignInAccount.getIdToken();
        Log.d("TAG#", "token3:" + token);
        String postUrl="https://thedreamsnetwork.com/postOp";
//        String postBody="{\n \"token\": \"" + token + "\",\n \"authCode\": \"" + authCode + "\"\n}";
        String postBody="{\n \"token\": \"" + token + "\"\n}";

        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(JSON, postBody);
        Request request = new Request.Builder()
                .url(postUrl)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("TAG", "send cancelled" + e);
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("TAG1",response.body().string());
            }
        });
    }

    class MainThread implements Runnable {
        public void run() {
            garageView= findViewById(R.id.imageView);
            mainSocket = new Socket();
            try {
                mainSocket.setReuseAddress(true);
                Log.d("TAG#", String.format("reuse:%b", mainSocket.getReuseAddress()));
                mainSocket.connect(new InetSocketAddress("thedreamsnetwork.com", 23));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // send src priv ip:port
            src_priv_ip = mainSocket.getLocalAddress().getHostName();
            src_priv_ip = getIPAddress(true);
            src_priv_port = mainSocket.getLocalPort();
            Log.d("MAHESH", String.format("%s %d", src_priv_ip, src_priv_port));
            send_msg(mainSocket, AESCipher.encrypt(String.format("%s:%d", src_priv_ip, src_priv_port)));

            //read src pub ip:port
            String[] client_pub_addr = AESCipher.decrypt(read_msg(mainSocket)).split(":");
            src_pub_ip = client_pub_addr[0];
            src_pub_port = Integer.parseInt(client_pub_addr[1]);



            //send src pub ip:port
            send_msg(mainSocket, AESCipher.encrypt(String.format("%s:%d", src_pub_ip, src_pub_port)));


            //read peer pub n priv ip:port
            String[] peer_addr = AESCipher.decrypt(read_msg(mainSocket)).split("\\|");


            String[] peer_pub_addr = peer_addr[0].split(":");
            String[] peer_priv_addr = peer_addr[1].split(":");
            Log.d("TAG#", "peer_pub_addr:"+peer_addr[0]);
            peer_pub_ip = peer_pub_addr[0];
            peer_pub_port = Integer.parseInt(peer_pub_addr[1]);
            peer_priv_ip = peer_priv_addr[0];
            peer_priv_port = Integer.parseInt(peer_priv_addr[1]);
            Log.d("TAG#", String.format("src priv ip:%s port:%d", src_priv_ip, src_priv_port));
            Log.d("TAG#", String.format("src pub ip:%s port:%d", src_pub_ip, src_pub_port));
            Log.d("TAG#", String.format("peer pub ip:%s port:%d", peer_pub_ip, peer_pub_port));
            Log.d("TAG#", String.format("peer priv ip:%s port:%d", peer_priv_ip, peer_priv_port));

            /*
            Connect connect_0 = new Connect(src_priv_ip, src_priv_port, peer_priv_ip, peer_priv_port);
            connect_0.start();
            */

            Connect connect_1 = new Connect(src_priv_ip, src_priv_port, peer_pub_ip, peer_pub_port);
            connect_1.start();

            Accept accept_0 = new Accept(src_priv_ip, src_priv_port);
            accept_0.start();

            try {

                Log.d("TAG#", "trying to join threads.");
                /*
                connect_0.join();
                Log.d("TAG#", "joined connect0");
                */

                connect_1.join();
                Log.d("TAG#", "joined connect1");

                accept_0.join();
                Log.d("TAG#", "joined accept0");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.d("TAG#", "all set:" + String.format("peer_ip:%s peer_port:%d", peer_ip, peer_port));
            dispatch_msgs();
        }
    }

    public void dispatch_msgs() {
        String data = "";
        send_msg(peer_socket, AESCipher.encrypt("garage_pic"));
        while (true) {
            data = "";
            Log.d("TAG#", "dispatch loop");
            lck.lock();
            Log.d("TAG#", "lock done");
            data = msg;
            msg = "";
            lck.unlock();
            Log.d("TAG#", "unlock done");
            if (!data.equals("")) {
                Log.d("TAG#", "printing data");
                Log.d("TAG#", data);
                send_msg(peer_socket, data);
                data = "";
            } else {
                try {
                    Log.d("TAG#", "reading data");
                    peer_socket.setSoTimeout(100);
                    data = read_msg(peer_socket);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                if (data != null) {
                    Log.d("TAG#", String.format("recvd image len:%d", data.length()));
                    byte[] img_data = AESCipher.decryptb(data);
                    if (img_data != null) {
                        Log.d("TAG#", String.format("Writing image %s", img_file.getPath()));
                        write_to_image(img_data, img_file);
                        new DownloadImageTask(garageView)
                                .execute(new String(img_file.getPath()));
                        send_msg(peer_socket, AESCipher.encrypt("garage_pic"));
                    }
                } else {
                    Log.d("TAG#", "no msg");
                }
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { }
        }
    }

    class Connect extends Thread {
        private String local_ip;
        private int local_port;
        private String ip;
        private int port;

        Connect(String l_ip, int l_port, String d_ip, int d_port) {
            this.local_ip = l_ip;
            this.local_port = l_port;
            this.ip = d_ip;
            this.port = d_port;
        }
        public void run() {
            InetSocketAddress st = new InetSocketAddress(local_ip, local_port);
            Socket s = null;
            int count = 0;
            Log.d("TAG#", String.format("connecting  from %s:%d to %s:%d ",
                    local_ip, local_port, ip, port));
            while (true) {
                try {
                    Log.d("TAG#", "connecting...");
                    s = new Socket();
                    s.setReuseAddress(true);
                    s.bind(st);
                    s.connect(new InetSocketAddress(ip, port));
                    Log.d("TAG#", String.format("connected from %s:%d to %s:%d success!",
                            local_ip, local_port, ip, port));
                    if (peer_socket == null) {
                        peer_ip = ip;
                        peer_port = port;
                        peer_socket = s;
                        peer_socket.setSoTimeout(120000);
                        Log.d("TAG#", "connect: initialized peer_socket");
                    }
                    else {
                        Log.d("TAG#", "connect:peer socket already inited");
                    }
                    break;
                } catch (SocketTimeoutException e) {
                    Log.d("TAG#", "timeout");
                    try {
                        s.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    if (peer_socket != null) {
                        Log.d("TAG#", "connect: peer socket already inited");
                        break;
                    } else {
                        continue;
                    }
                }
                catch (BindException e) {
                    e.printStackTrace();
                    try {
                        s.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    break;
                }
                catch (IOException e) {
                    e.printStackTrace();
                    try {
                        s.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    if (peer_socket != null) {
                        Log.d("TAG#", "peer socket already inited");
                        break;
                    } else {
                        continue;
                    }
                }
            }
        }
    }

    public class Accept extends Thread {
        private String local_ip;
        private int local_port;
        ServerSocket ss;

        Accept(String l_ip, int l_port)  {
            this.local_ip = l_ip;
            this.local_port = l_port;
        }

        public void run() {
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.setSoTimeout(100);
                ss.bind(new InetSocketAddress(local_ip, local_port));
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (true) {
                try {
                    Log.d("TAG#", String.format("accept thread at :%d", this.local_port));
                    Socket sn = ss.accept();
                    Log.d("TAG#", String.format("Accept %d Connected!", local_port));
                    if (peer_socket == null) {
                        InetSocketAddress snt = (InetSocketAddress)sn.getRemoteSocketAddress();
                        peer_ip = snt.getAddress().getHostAddress();
                        peer_port = snt.getPort();
                        Log.d("TAG#", String.format("Peer ip:%s port:%d", peer_ip, peer_port));
                        ss.close();
                        peer_socket = sn;
                        peer_socket.setSoTimeout(120000);
                        Log.d("TAG#", String.format("accept: initialized peer_socket by accept thread at %s", local_port));
                    } else {
                        Log.d("TAG#", "accept: peer socket already inited");
                    }
                    break;
                } catch (IOException e) {
                    if (peer_socket != null) {
                        Log.d("TAG#", "accept: peer socket already inited");
                        try {
                            ss.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        break;
                    } else {
                        continue;
                    }
                }
            }
        }
    }

    public  static class AESCipher {
        public static final String key = "$434341qgdfdsa34";

        // String plaintext -> Base64-encoded String ciphertext
        public static String encrypt(String plaintext) {
            try {
                // Generate a random 16-byte initialization vector
                byte initVector[] = new byte[16];
                (new Random()).nextBytes(initVector);
                IvParameterSpec iv = new IvParameterSpec(initVector);

                // prep the key
                SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

                // prep the AES Cipher
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
                cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

                // Encode the plaintext as array of Bytes
                byte[] cipherbytes = cipher.doFinal(plaintext.getBytes());

                // Build the output message initVector + cipherbytes -> base64
                byte[] messagebytes = new byte[initVector.length + cipherbytes.length];

                System.arraycopy(initVector, 0, messagebytes, 0, 16);
                System.arraycopy(cipherbytes, 0, messagebytes, 16, cipherbytes.length);

                // Return the cipherbytes as a Base64-encoded string
                return Base64.encodeToString(messagebytes, 0);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }

        // Base64-encoded String ciphertext -> String plaintext
        public static String decrypt(String ciphertext) {
            try {
                if (ciphertext.length() < 1) return null;
                byte[] cipherbytes = Base64.decode(ciphertext, 0);

                byte[] initVector = Arrays.copyOfRange(cipherbytes, 0, 16);

                byte[] messagebytes = Arrays.copyOfRange(cipherbytes, 16, cipherbytes.length);

                IvParameterSpec iv = new IvParameterSpec(initVector);
                SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
                cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

                // Convert the ciphertext Base64-encoded String back to bytes, and
                // then decrypt
                byte[] byte_array = cipher.doFinal(messagebytes);

                // Return plaintext as String
                return new String(byte_array, StandardCharsets.UTF_8);

            } catch (Exception ex) {
                ex.printStackTrace();
            }

            return null;
        }

        // Base64-encoded String ciphertext -> byte[]
        public static byte[] decryptb(String ciphertext) {
            try {
                if (ciphertext.length() < 1) return null;
                Log.d("TAG#", String.format("before decrypt:%s", md5(ciphertext)));
                Log.d("TAG#", String.format("x:%s", ciphertext));

                byte[] cipherbytes = Base64.decode(ciphertext, 0);

                byte[] initVector = Arrays.copyOfRange(cipherbytes, 0, 16);

                byte[] messagebytes = Arrays.copyOfRange(cipherbytes, 16, cipherbytes.length);

                IvParameterSpec iv = new IvParameterSpec(initVector);
                SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
                cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

                // Convert the ciphertext Base64-encoded String back to bytes, and
                // then decrypt
                byte[] byte_array = cipher.doFinal(messagebytes);
                String x = new String(byte_array);
                Log.d("TAG#", String.format("after decrypt:%s", md5(x)));
                return Base64.decode(byte_array, 0);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            return null;
        }
    }

    private void write_to_image(byte[] x, File file) {

        if (!file.exists()) {
            Log.d("creating file", file.toString());
        } else {
            Log.d("overwriting file", file.toString());
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                fos.write(x);
                fos.flush();
                fos.close();
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }

    }
    private void send_msg(Socket mainSocket, String data) {
        try {
            byte[] data_len = ByteBuffer.allocate(4).putInt(data.length()).array();
            PrintWriter outData = null;
            outData = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mainSocket.getOutputStream())));
            outData.print(new String(data_len));
            outData.print(data);
            outData.flush();
            Log.d("TAG#", String.format("sent data:%s", data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String read_msg(Socket mainSocket) {
        InputStream in = null;
        try {
            in = mainSocket.getInputStream();
            Log.d("TAG#", String.format("avail:%d", in.available()));
            byte[] b = new byte[4];
            in.read(b, 0, 4);
            ByteBuffer bf = ByteBuffer.wrap(b);
            int data_len = bf.getInt();
            Log.d("TAG#", String.format("datalen:%d", data_len));
            if (data_len > 1000000)
                return null;

            b = new byte[data_len];
            in.read(b, 0, data_len);
            String data = new String(b);
            Log.d("TAG#", String.format("data:%s", data));
            return data;
        } catch (SocketException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
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

    public static final String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... data) {
            String img_path = data[0];
            Log.d("TAG#", String.format("img:%s", img_path));
            Bitmap bitmap = BitmapFactory.decodeFile(img_path);
            return bitmap;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);

        }
    }
}