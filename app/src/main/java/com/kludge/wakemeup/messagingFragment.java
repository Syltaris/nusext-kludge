package com.kludge.wakemeup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/*
 * Created by Yu Peng on 13/7/2016.
 */
public class MessagingFragment extends ListFragment {

    public String userId, targetId;
    private boolean isP2PReceiverRegistered;
    private ArrayList<Pair<String, String>> messageArrayList;
    private MessageAdapter messageAdapter;
    private long alarmId;

    private TextToSpeech mTextToSpeech;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_messaging, container, false);

        // create adapter for messages ListView
        ListView messageList = (ListView) rootView.findViewById(android.R.id.list);
        assert messageList != null;
        messageArrayList = new ArrayList<>();
        messageAdapter = new MessageAdapter(getContext(), messageArrayList);

        setListAdapter(messageAdapter);


        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // get userId and targetId from SharedPreferences
        userId = getActivity().getSharedPreferences("preferences_user", getActivity().MODE_PRIVATE).getString("userId", "");

        alarmId = getActivity().getIntent().getLongExtra("alarmId", -1);

        AlarmDetails alarm = AlarmLab.get(getContext()).getAlarmDetails(alarmId);

        // *** potentially more than 1 unique targetId, shouldn't store in SharedPrefs; should be in AlarmDetails ***

        if (alarm == null) // when waker starts fragment from MessagingActivity
            targetId = getArguments().getString("targetId");
        else // when wakee starts fragment from AlarmWake
            targetId = alarm.getTargetId();
        Log.i("Messaging Fragment", "targetId is " + targetId);

        final EditText mP2PMessageEditText = (EditText) view.findViewById(R.id.P2PMessageEditText);
        Button mSendP2PMessageButton = (Button) view.findViewById(R.id.sendP2PMessageButton);


        mSendP2PMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mP2PMessageEditText.getText().toString();
                mP2PMessageEditText.getText().clear();

                System.out.println(userId + " " + targetId + " " + message);

                sendP2PMessage(message);

                // save message to messageArrayList to be displayed by P2PMessageListView
                Pair<String, String> outgoingP2PMessage = new Pair<>(userId, message);
                messageArrayList.add(outgoingP2PMessage);

                // refresh messageAdapter
                messageAdapter.notifyDataSetChanged();

                registerReceiver();

            }
        });

        initTextToSpeech();
    }

    private void initTextToSpeech(){
        mTextToSpeech = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR)
                    mTextToSpeech.setLanguage(Locale.UK);
                else
                    Toast.makeText(getContext(), "textToSpeech init failed", Toast.LENGTH_LONG).show();
            }
        });
    }


    // post P2P message to server
    private void sendP2PMessage(String message) {
        new ServletPostAsyncTask().execute(new GCMParams(
                getContext(), "sendP2PMessage", userId, "", targetId, "", message, ""));
    }

    // set broadcast receiver to listen from GCMListenerService
    private BroadcastReceiver mP2PMessageBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String message = intent.getStringExtra("message");
            String targetId = intent.getStringExtra("targetId");

            Pair<String, String> incomingP2PMessage = new Pair<>(targetId, message);
            messageArrayList.add(incomingP2PMessage);

            // refresh messageAdapter
            messageAdapter.notifyDataSetChanged();

            // read out incomingP2P message
            mTextToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, "TextToSpeechIncomingP2PMessage");
        }
    };

    private void registerReceiver() {
        if (!isP2PReceiverRegistered) {
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mP2PMessageBroadcastReceiver,
                    new IntentFilter("incomingP2PMessage"));
            isP2PReceiverRegistered = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // re-register P2P broadcast receiver
        registerReceiver();

        initTextToSpeech();
    }

    @Override
    public void onPause() {
        // unregister P2P message broadcast receiver
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mP2PMessageBroadcastReceiver);
        isP2PReceiverRegistered = false;

        // release text to speech resources
        mTextToSpeech.stop();
        mTextToSpeech.shutdown();

        super.onPause();
    }
}

//arrayAdapter for the 'scoreboard'
class MessageAdapter extends ArrayAdapter<Pair<String, String>>{

    public MessageAdapter(Context context, ArrayList<Pair<String, String>> messageInfo){
        super(context, 0, messageInfo);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        //retrieve individual userInfo
        final Pair<String, String> messageInfo = getItem(position);

        String userId = getContext().getSharedPreferences("preferences_user", Context.MODE_PRIVATE).getString("userId", "");
        TextView message;

        if (messageInfo.first.equals(userId)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_list_item_right, parent, false);

            message = (TextView) convertView.findViewById(R.id.message_textview);
            message.setText(userId + ": " + messageInfo.second);
        }
        else {
            String targetId = messageInfo.first;
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_list_item_left, parent, false);

            message = (TextView) convertView.findViewById(R.id.message_textview);
            message.setText(targetId + ": " + messageInfo.second);
        }

        return convertView;
        //super.getView(position, convertView, parent);
    }
}