package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import static java.lang.StrictMath.round;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */

public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORT = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    static int count = 0;

    String clientmsg[][] = new String[100][7];
    int clientmsgcnt=0;
    int seqnum_client = 0;
    float maxseq = 0;
    String my_Port = "";
    static boolean fail_checked_client=false;
    static boolean fail_checked_server=false;

    String Global_Failed_node=null;

    Comparator<Msg> MsgCompare = new MsgComparator();
    BlockingQueue<Msg> pq= new PriorityBlockingQueue<Msg>(100, MsgCompare);
    BlockingQueue<Msg> pq1= new PriorityBlockingQueue<Msg>(100, MsgCompare);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);


        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        my_Port = myPort;

        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final Button button = (Button) findViewById(R.id.button4);

        final EditText editText = (EditText) findViewById(R.id.editText1);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                {
                    String msg = editText.getText().toString() + "\n";
                    editText.setText("");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
                }
            }
        });

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT, 100);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public class Msg {
        String msg;
        float msgid;
        float seqnum;
        boolean deliver;

        public Msg()
        { }

        public Msg(String m, float msid, float sn, boolean b)
        {
            this.msg=m;
            this.msgid=msid;
            this.seqnum=sn;
            this.deliver=b;
        }
    }

    public class MsgComparator implements Comparator<Msg>
    {
        @Override
        public int compare(Msg m1, Msg m2)
        {

            if (m1.seqnum < m2.seqnum)
            {
                return -1;
            }
            if (m1.seqnum > m2.seqnum)
            {
                return 1;
            }
            return 0;
        }
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            Log.e("socket", String.valueOf(sockets[0]));


            try {
                while (true) {
                    Socket s = serverSocket.accept();

                    InputStreamReader isr = new InputStreamReader(s.getInputStream());
                    BufferedReader br = new BufferedReader(isr);
                    PrintWriter pw = new PrintWriter(s.getOutputStream(), true);

                    String msg = br.readLine();

                    Log.e ("Server msg recieved", msg+" "+String.valueOf(s.getLocalPort()) );

                    if (msg==null);
                    {
                        Log .e ("ATTENTION !!!!!!!!", String.valueOf(s.getPort()));
                    }

                    char a = msg.charAt(0);
                    if (a == 'N') {

                        String[] element = msg.split("[\\s]");

                        Msg ms = new Msg(element[3], Float.valueOf(element[1]), Float.valueOf(element[2]), false);

                        pq.add(ms);

                        maxseq++;

                        int sendmaxseq=round(maxseq);

                        pw.println(sendmaxseq);

                    }

                    if (a == 'R') {

                        String[] element1 = msg.split("[\\s]");

                        //Iterator: Updating the seq num and deliverable status
                        while(pq.size()>0) {

                            Msg msnew=pq.poll();

                            if (msnew.msgid==Float.valueOf(element1[1])) {
                                msnew.seqnum = Float.valueOf(element1[2]);
                                msnew.deliver = true;

                                if (maxseq < msnew.seqnum) {
                                    maxseq = msnew.seqnum;
                                }
                            }
                            pq1.add(msnew);
                        }
                        pq1.drainTo(pq);

                        //Delivering the peek results which are deliverable
                        Boolean Flag = true;
                        while (Flag) {
                            if (pq.peek() != null) {

                                Msg ms1 = pq.peek();
                                String msid=String.valueOf(ms1.msgid);
                                String[] node= msid.split("[.]");
                                String node_server=node[0];

                                if(node_server.equals(Global_Failed_node))
                                {
                                    pq.remove();
                                    Log.e ("Remove from R", node_server);
                                    continue;
                                }

                                else {
                                    if (ms1.deliver) {
                                        ContentValues mNewValues = new ContentValues();
                                        mNewValues.put("key", String.valueOf(count));
                                        mNewValues.put("value", ms1.msg);
                                        count++;

                                        ContentResolver mContentResolver = getContentResolver();
                                        mContentResolver.insert(mUri, mNewValues);

                                        publishProgress(ms1.msg);

                                        pq.remove();
                                    } else if (ms1.deliver == false) {
                                        Flag = false;
                                    }
                                }

                            }
                            else {
                                Flag = false;
                            }
                        }
                        pw.println("Received");
                    }

                    if (a == 'F') {

                        //Handling the failed node
                        String[] element2 = msg.split("[\\s]");
                        String fail_node_client=element2[1];

                        while(pq.size()>0) {

                            Msg msnew=pq.poll();

                            String msid=String.valueOf(msnew.msgid);
                            String[] node= msid.split("[.]");
                            String node_server=node[0];

                            if (node_server.equals(fail_node_client))
                            {
                                Log.e("Remove", node_server);
                                continue;
                            }
                            else {
                                Log.e("Not Remove", node_server);
                                pq1.add(msnew);
                            }
                        }
                        pq1.drainTo(pq);
                        fail_checked_server=true;
                    }

                    br.close();
                    s.close();
                }

            } catch (IOException e) {
                Log.e("Server IO Exception", "Server connection not established");
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView textView = (TextView) findViewById(R.id.textView1);
            textView.append(strReceived + "\n");

        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            float localmax=0;
            int localmsgcnt=clientmsgcnt;
            clientmsgcnt++;
            seqnum_client++;
            boolean fail_flag=false;
            String failed_node="";

            try{
                for (int k = 0; k < 5; k++) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORT[k]));

                        PrintWriter send = new PrintWriter(socket.getOutputStream(), true);
                        InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                        BufferedReader br = new BufferedReader(isr);

                        String msgToSend = msgs[0];

                        String s = "N ";
                        s = s.concat(my_Port);
                        s = s.concat(".");
                        s = s.concat(String.valueOf(seqnum_client));
                        s = s.concat(" ");
                        s = s.concat(String.valueOf(0));
                        s = s.concat(" ");
                        s = s.concat(String.valueOf(msgToSend));

                        Log.e(" Client msg 1", s);
                        send.println(s);

                        String fromserver = String.valueOf(0);

                        socket.setSoTimeout(10000);
                        try {
                            fromserver = br.readLine();
                        }catch (SocketException e) {
                            fail_flag = true;
                            failed_node = REMOTE_PORT[k];
                            fromserver = String.valueOf(0);
                            Log.e("Timeout Exception", e.toString());
                        } catch (Exception e) {
                            Log.e("Timeout Exception", e.toString());
                        }

                        Log.e("In Client from server", k + " " + fromserver);
                        if (fromserver==null) {
                            fail_flag = true;
                            failed_node = REMOTE_PORT[k];
                            Global_Failed_node=REMOTE_PORT[k];
                            fromserver = String.valueOf(0);
                        }

                        String s1 = my_Port;
                        s1 = s1.concat(".");
                        s1 = s1.concat(String.valueOf(seqnum_client));

                        clientmsg[localmsgcnt][0] = s1; //msgid
                        clientmsg[localmsgcnt][1 + k] = fromserver; //maxseq sent by each server

                        socket.close();
                    } catch (UnknownHostException e) {
                        Log.e("Inside Host Exception 1", "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e("Inside IO Exception 1", e.toString());
                    }
                }


                for (int k=0; k<5; k++) {
                    if(localmax<Float.valueOf(clientmsg[localmsgcnt][1 + k])) {
                        localmax=Float.valueOf(clientmsg[localmsgcnt][1 + k]);
                    }
                    clientmsg[localmsgcnt][6]=String.valueOf(localmax); //final maxseq num
                }

                for (int k = 0; k < 5; k++) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORT[k]));

                        String s = "R ";
                        s = s.concat(clientmsg[localmsgcnt][0]);
                        s = s.concat(" ");
                        s = s.concat(clientmsg[localmsgcnt][6]);
                        s = s.concat(String.valueOf(my_Port));

                        PrintWriter send = new PrintWriter(socket.getOutputStream(), true);
                        Log.e("Client msg 3", s);
                        send.println(s);

                        InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                        BufferedReader br = new BufferedReader(isr);

                        String fromserver = br.readLine();

                        Log.e("In Client from server", k + " " + fromserver);
                        if (fromserver==null) {
                            fail_flag = true;
                            failed_node = REMOTE_PORT[k];
                            Global_Failed_node=REMOTE_PORT[k];
                        }

                        socket.close();
                    } catch (UnknownHostException e) {
                        Log.e("Inside Host Exception 2", e.toString());
                    } catch (IOException e) {
                        Log.e("Inside IO Exception 2", e.toString());
                    }
                }

                if (fail_flag==true ) {
                    for (int k = 0; k < 5; k++) {
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT[k]));

                            String s = "F ";
                            s = s.concat(failed_node);

                            PrintWriter send = new PrintWriter(socket.getOutputStream(), true);
                            Log.e("Client msg 4", s);
                            send.println(s);

                            socket.close();
                        }catch (UnknownHostException e) {
                            Log.e("Failure Exception", e.toString());
                        }catch (IOException e) {
                            Log.e("Failure Exception", e.toString());
                        }
                    }
                    fail_checked_client=true;
                }


            } catch (Exception e) {
                Log.e("Outside Exception", e.toString());
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
