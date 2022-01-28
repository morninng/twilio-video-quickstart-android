package com.twilio.video.quickstart.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.koushikdutta.ion.Ion;
import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioDevice.BluetoothHeadset;
import com.twilio.audioswitch.AudioDevice.Earpiece;
import com.twilio.audioswitch.AudioDevice.Speakerphone;
import com.twilio.audioswitch.AudioDevice.WiredHeadset;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.video.AudioCodec;
import com.twilio.video.BandwidthProfileMode;
import com.twilio.video.BandwidthProfileOptions;
import com.twilio.video.ClientTrackSwitchOffControl;
import com.twilio.video.ConnectOptions;
import com.twilio.video.EncodingParameters;
import com.twilio.video.G722Codec;
import com.twilio.video.H264Codec;
import com.twilio.video.IsacCodec;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalTrackPublicationOptions;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.LocalVideoTrackPublication;
import com.twilio.video.OpusCodec;
import com.twilio.video.PcmaCodec;
import com.twilio.video.PcmuCodec;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TrackPriority;
import com.twilio.video.TrackSwitchOffMode;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoBandwidthProfileOptions;
import com.twilio.video.VideoCodec;
import com.twilio.video.VideoContentPreferences;
import com.twilio.video.VideoContentPreferencesMode;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;
import com.twilio.video.Vp8Codec;
import com.twilio.video.Vp9Codec;
import com.twilio.video.quickstart.BuildConfig;
import com.twilio.video.quickstart.R;
import com.twilio.video.quickstart.dialog.Dialog;
import com.twilio.video.quickstart.util.CameraCapturerCompat;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Timer;
import kotlin.Unit;
import kotlin.concurrent.TimersKt;
import tvi.webrtc.VideoSink;

public class VideoActivity extends AppCompatActivity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final int CAMERA_PERMISSION_INDEX = 0;
    private static final int MIC_PERMISSION_INDEX = 1;
    private static final String TAG = "VideoActivity";

    /*
     * Audio and video tracks can be created with names. This feature is useful for categorizing
     * tracks of participants. For example, if one participant publishes a video track with
     * ScreenCapturer and CameraCapturer with the names "screen" and "camera" respectively then
     * other participants can use RemoteVideoTrack#getName to determine which video track is
     * produced from the other participant's screen or camera.
     */
    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";

    /*
     * You must provide a Twilio Access Token to connect to the Video service
     */
    private static final String TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN;
    private static final String ACCESS_TOKEN_SERVER = BuildConfig.TWILIO_ACCESS_TOKEN_SERVER;

    /*
     * Access token used to connect. This field will be set either from the console generated token
     * or the request to the token server.
     */
    private String accessToken;


    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private Room room;
    private LocalParticipant localParticipant;

    /*
     * AudioCodec and VideoCodec represent the preferred codec for encoding and decoding audio and
     * video.
     */
    private AudioCodec audioCodec;
    private VideoCodec videoCodec;

    /*
     * Encoding parameters represent the sender side bandwidth constraints.
     */
    private EncodingParameters encodingParameters;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;

    /*
     * Android shared preferences used for settings
     */
    private SharedPreferences preferences;

    /*
     * Android application UI elements
     */
    private CameraCapturerCompat cameraCapturerCompat;
//    private VideoFormat videoFormat;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private FloatingActionButton connectActionFab;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton muteActionFab;
    private FloatingActionButton refreshTrackActionFab;
    private FloatingActionButton refreshRoomActionFab;
    private ProgressBar reconnectingProgressBar;
    private AlertDialog connectDialog;
    private String remoteParticipantIdentity;

    /*
     * Audio management
     */
    private AudioSwitch audioSwitch;
    private int savedVolumeControlStream;
    private MenuItem audioDeviceMenuItem;

    private VideoSink localVideoView;
    private boolean disconnectedFromOnDestroy;
    private boolean enableAutomaticSubscription;

    // added by moriyama
    private String roomNameStored;
    private boolean isRefresh;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isRefresh = false;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        primaryVideoView = findViewById(R.id.primary_video_view);
        thumbnailVideoView = findViewById(R.id.thumbnail_video_view);
        reconnectingProgressBar = findViewById(R.id.reconnecting_progress_bar);

        connectActionFab = findViewById(R.id.connect_action_fab);

        refreshRoomActionFab = findViewById(R.id.refresh_video_room); //  refresh token

        
        refreshTrackActionFab = findViewById(R.id.refresh_video_track); //  refresh token
        switchCameraActionFab = findViewById(R.id.switch_camera_action_fab); // camera switching
        localVideoActionFab = findViewById(R.id.local_video_action_fab);
        muteActionFab = findViewById(R.id.mute_action_fab);




        /*
         * Get shared preferences to read settings
         */
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        /*
         * Setup audio management and set the volume control stream
         */
        audioSwitch = new AudioSwitch(getApplicationContext());
        savedVolumeControlStream = getVolumeControlStream();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        /*
         * Check camera and microphone permissions. Needed in Android M. Also, request for bluetooth
         * permissions for enablement of bluetooth audio routing.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraMicrophoneAndBluetooth();
        } else {
            audioSwitch.start(
                    (audioDevices, audioDevice) -> {
                        updateAudioDeviceIcon(audioDevice);
                        return Unit.INSTANCE;
                    });
            createAudioAndVideoTracks();   // main customization part
            setAccessToken();
        }

        /*
         * Set the initial state of the UI
         */
        intializeUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_video_activity, menu);
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device);
        // AudioSwitch has already started and thus notified of the initial selected device
        // so we need to updates the UI
        updateAudioDeviceIcon(audioSwitch.getSelectedAudioDevice());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.menu_audio_device:
                showAudioDevices();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            /*
             * The first two permissions are Camera & Microphone, bluetooth isn't required but
             * enabling it enables bluetooth audio routing functionality.
             */
            boolean cameraAndMicPermissionGranted =
                    (PackageManager.PERMISSION_GRANTED == grantResults[CAMERA_PERMISSION_INDEX])
                            & (PackageManager.PERMISSION_GRANTED
                                    == grantResults[MIC_PERMISSION_INDEX]);

            /*
             * Due to bluetooth permissions being requested at the same time as camera and mic
             * permissions, AudioSwitch should be started after providing the user the option
             * to grant the necessary permissions for bluetooth.
             */
            audioSwitch.start(
                    (audioDevices, audioDevice) -> {
                        updateAudioDeviceIcon(audioDevice);
                        return Unit.INSTANCE;
                    });

            if (cameraAndMicPermissionGranted) {
                createAudioAndVideoTracks();
                setAccessToken();
            } else {
                Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show();
            }
        }
    }





    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {

        System.out.println("<<<<<<<<<<onResume>>>>>>>>>>>>>");


        super.onResume();
        /*
         * Update preferred audio and video codec in case changed in settings
         */
        audioCodec =
                getAudioCodecPreference(
                        SettingsActivity.PREF_AUDIO_CODEC,
                        SettingsActivity.PREF_AUDIO_CODEC_DEFAULT);
        videoCodec =
                getVideoCodecPreference(
                        SettingsActivity.PREF_VIDEO_CODEC,
                        SettingsActivity.PREF_VIDEO_CODEC_DEFAULT);
        enableAutomaticSubscription =
                getAutomaticSubscriptionPreference(
                        SettingsActivity.PREF_ENABLE_AUTOMATIC_SUBSCRIPTION,
                        SettingsActivity.PREF_ENABLE_AUTOMATIC_SUBSCRIPTION_DEFAULT);
        /*
         * Get latest encoding parameters
         */
        final EncodingParameters newEncodingParameters = getEncodingParameters();

        /*
         * If the local video track was released when the app was put in the background, recreate.
         */



        if (localVideoTrack == null && checkPermissionForCameraAndMicrophone()) {

            System.out.println("<<<<<<<<<<< create local track by resume >>>>>>>>>>>>>>>");
            VideoFormat videoFormat;
            if(isRefresh){
                System.out.println("<<<<<<<<<< video format with VGA dimension for refresh");
            videoFormat = new VideoFormat(VideoDimensions.VGA_VIDEO_DIMENSIONS, 15); //HD_1080P_VIDEO_DIMENSIONS
            }else{
                System.out.println("<<<<<<<<<< video format with 1080 dimension original");
                
            videoFormat = new VideoFormat(VideoDimensions.HD_1080P_VIDEO_DIMENSIONS, 15); //HD_1080P_VIDEO_DIMENSIONS
            }



            localVideoTrack =
                    LocalVideoTrack.create(
                            this, true, cameraCapturerCompat, videoFormat, LOCAL_VIDEO_TRACK_NAME);
            localVideoTrack.addSink(localVideoView);

            /*
             * If connected to a Room then share the local video track.
             */
            if (localParticipant != null) {


                System.out.println("-----------------publish switch --------------");

//                publishVideoTrackWithPriorityXXX();
                publishVideoTrack();

                /*
                 * Update encoding parameters if they have changed.
                 */
                if (!newEncodingParameters.equals(encodingParameters)) {
                    localParticipant.setEncodingParameters(newEncodingParameters);
                }
            }
        }

        /*
         * Update encoding parameters
         */
        encodingParameters = newEncodingParameters;

        /*
         * Update reconnecting UI
         */
        if (room != null) {
            reconnectingProgressBar.setVisibility(
                    (room.getState() != Room.State.RECONNECTING) ? View.GONE : View.VISIBLE);
        }
    }


    private void publishVideoTrack(){
        System.out.println("-----------------publishPriorityXxxxVideo -----------------");
        localParticipant.publishTrack(localVideoTrack);
    }

    private void publishVideoTrackWithPriorityXXX(){
                System.out.println("-----------------publishPriorityXxxxVideo -----------------");
                LocalTrackPublicationOptions localTrackPublicationOptions = new LocalTrackPublicationOptions(TrackPriority.HIGH);
                localParticipant.publishTrack(localVideoTrack, localTrackPublicationOptions);
    }


    private void setPriorityTimer(){

                TimerTask task = new TimerTask() {
                    public void run() {
                        getPriority();
                    }
                };

                Timer timer = new Timer();
                timer.schedule(task, 10000, 5000);
    }


    protected void getPriority() {
        List<LocalVideoTrackPublication> localVideotrackPublicationList = localParticipant.getLocalVideoTracks();

        int size = localVideotrackPublicationList.size();
        if(size > 0){
            System.out.println("-------------------- localvideopublication exist");
            LocalVideoTrackPublication localVideotrackPublication =  localVideotrackPublicationList.get(0);
            if(localVideotrackPublication != null){

                TrackPriority priority = localVideotrackPublication.getPriority();
                Log.d("ssss", "<<<<<<<<<<----------->>>>>>>>>> priority >>>>>>>>>>> " + priority);
            }else{

                Log.d("ssss", "<<<<<<<<<<------localVideotrackPublication is null ----->>>>>> ");
            }

        }else{
            System.out.println("-------------------- no localvideopublication");
        }
    }




    protected void setPriorityXXXX() {
        List<LocalVideoTrackPublication> localVideotrackPublicationList = localParticipant.getLocalVideoTracks();

        int size = localVideotrackPublicationList.size();
        if(size > 0){
            System.out.println("-------------------- localvideopublication exist");
            LocalVideoTrackPublication localVideotrackPublication =  localVideotrackPublicationList.get(0);
            if(localVideotrackPublication != null){

                localVideotrackPublication.setPriority(TrackPriority.HIGH);
                Log.d("ssss", "<<<<<<<<<<----------->>>>>>>>>> setPriorityXXXX >>>>>>>>>>> ");
            }else{
                Log.d("ssss", "<<<<<<<<<<------localVideotrackPublication is null ----->>>>>> ");

            }
        }else{
            System.out.println("-------------------- no localvideopublication");
        }
    }




    @Override
    protected void onPause() {
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, unpublish from room before
             * releasing the video track. Participants will be notified that the track has been
             * unpublished.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }

            localVideoTrack.release();
            localVideoTrack = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        /*
         * Tear down audio management and restore previous volume stream
         */
        audioSwitch.stop();
        setVolumeControlStream(savedVolumeControlStream);

        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }

        super.onDestroy();
    }

    private boolean checkPermissions(String[] permissions) {
        boolean shouldCheck = true;
        for (String permission : permissions) {
            shouldCheck &=
                    (PackageManager.PERMISSION_GRANTED
                            == ContextCompat.checkSelfPermission(this, permission));
        }
        return shouldCheck;
    }

    private void requestPermissions(String[] permissions) {
        boolean displayRational = false;
        for (String permission : permissions) {
            displayRational |=
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
        }
        if (displayRational) {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this, permissions, CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkPermissionForCameraAndMicrophone() {
        return checkPermissions(
                new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
    }

    private void requestPermissionForCameraMicrophoneAndBluetooth() {
        String[] permissionsList;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionsList =
                    new String[] {
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.BLUETOOTH_CONNECT
                    };
        } else {
            permissionsList =
                    new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        }
        requestPermissions(permissionsList);
    }







    // main customization part

    private <VideoConstraints> void createAudioAndVideoTracks() {
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);

        // Share your camera
        cameraCapturerCompat =
                new CameraCapturerCompat(this, CameraCapturerCompat.Source.FRONT_CAMERA);






        // updated



// HD_1080P_VIDEO_DIMENSIONS
// HD Widescreen 1080P (1920 x 1080) resolution

//HD_720P_VIDEO_DIMENSIONS
//HD 720P (1280 x 720) resolution
//        HD_960P_VIDEO_DIMENSIONS
//        HD 960P (1280 x 960) resolution
//HD_540P_VIDEO_DIMENSIONS
//HD 540P (960 x 540) resolution

//        CIF_VIDEO_DIMENSIONS
//CIF (352 x 288) resolution in 1.22:1 aspect ratio

        // VideoFormat videoFormat = new VideoFormat(VideoDimensions.HD_720P_VIDEO_DIMENSIONS, 30); //

        // VideoFormat videoFormat = new VideoFormat(VideoDimensions.HD_1080P_VIDEO_DIMENSIONS, 30); //HD_1080P_VIDEO_DIMENSIONS
//        VideoFormat videoFormat = new VideoFormat(VideoDimensions.HD_1080P_VIDEO_DIMENSIONS, 15); //HD_1080P_VIDEO_DIMENSIONS
        VideoFormat videoFormat = new VideoFormat(VideoDimensions.HD_1080P_VIDEO_DIMENSIONS, 15); //HD_1080P_VIDEO_DIMENSIONS



//        VideoConstraints videoConstraints =
//                new VideoConstraints.Builder()
//                        .maxFps(24)
//                        .maxVideoDimensions(VideoDimensions.VGA_VIDEO_DIMENSIONS)
//                        .build();



        System.out.println("<<<<<<<<<<<<<<< original local video track create >>>>>>>>>>>");

        localVideoTrack =
                LocalVideoTrack.create(this, true, cameraCapturerCompat,videoFormat , LOCAL_VIDEO_TRACK_NAME);
        primaryVideoView.setMirror(true);
        localVideoTrack.addSink(primaryVideoView);
        localVideoView = primaryVideoView;
    }

    private void setAccessToken() {
        System.out.println(">>>>>> setAccessToken setAccessToken<<<<<<<<<<<<<<<<<<");
        if (!BuildConfig.USE_TOKEN_SERVER) {
            /*
             * OPTION 1 - Generate an access token from the getting started portal
             * https://www.twilio.com/console/video/dev-tools/testing-tools and add
             * the variable TWILIO_ACCESS_TOKEN setting it equal to the access token
             * string in your local.properties file.
             */





            System.out.println("-----ee------ WILIO_ACCESS_TOKEN -----");
            System.out.println(TWILIO_ACCESS_TOKEN);
            this.accessToken = TWILIO_ACCESS_TOKEN;
        } else {
            System.out.println("----------  USE_TOKEN_SERVER -------------");
            /*
             * OPTION 2 - Retrieve an access token from your own web app.
             * Add the variable ACCESS_TOKEN_SERVER assigning it to the url of your
             * token server and the variable USE_TOKEN_SERVER=true to your
             * local.properties file.
             */



             
            retrieveAccessTokenfromServer();
        }
    }

    private void connectToRoom(String roomName) {


        
        roomNameStored = roomName;
        System.out.println("connectToRoom----------------");
        System.out.println(accessToken);
        audioSwitch.activate();
        ConnectOptions.Builder connectOptionsBuilder =
                new ConnectOptions.Builder(accessToken).roomName(roomName);

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            connectOptionsBuilder.audioTracks(Collections.singletonList(localAudioTrack));
        }

        /*
         * Add local video track to connect options to share with participants.
         */
        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));

        }

        /*
         * Set the preferred audio and video codec for media.
         */
        connectOptionsBuilder.preferAudioCodecs(Collections.singletonList(audioCodec));
        connectOptionsBuilder.preferVideoCodecs(Collections.singletonList(videoCodec));

        /*
         * Set the sender side encoding parameters.
         */
        connectOptionsBuilder.encodingParameters(encodingParameters);

        /*
         * Toggles automatic track subscription. If set to false, the LocalParticipant will receive
         * notifications of track publish events, but will not automatically subscribe to them. If
         * set to true, the LocalParticipant will automatically subscribe to tracks as they are
         * published. If unset, the default is true. Note: This feature is only available for Group
         * Rooms. Toggling the flag in a P2P room does not modify subscription behavior.
         */

        // updated part


        connectOptionsBuilder.enableAutomaticSubscription(enableAutomaticSubscription)
                .bandwidthProfile(new BandwidthProfileOptions(new VideoBandwidthProfileOptions.Builder()
                        .dominantSpeakerPriority(TrackPriority.HIGH)
//                        .dominantSpeakerPriority(TrackPriority.LOW)
                        // .dominantSpeakerPriority(TrackPriority.STANDARD)
                        .mode(BandwidthProfileMode.PRESENTATION)
                        // .mode(BandwidthProfileMode.GRID) // default
//                        .trackSwitchOffMode(TrackSwitchOffMode.DETECTED)
//                          .trackSwitchOffMode(TrackSwitchOffMode.PREDICTED) // default
                        .trackSwitchOffMode(TrackSwitchOffMode.DISABLED)
                         .clientTrackSwitchOffControl(ClientTrackSwitchOffControl.AUTO)
                        .videoContentPreferencesMode(VideoContentPreferencesMode.MANUAL) // 影響大きそう。
//                         .videoContentPreferencesMode(VideoContentPreferencesMode.AUTO) // default
                        .maxSubscriptionBitrate(0L)
                        .build()
                ));



        System.out.println("<<<<<<<<<<<<<<<<<<connect connect connect>>>>>>>>>>>>>>");
        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());





        setDisconnectAction();
    }

    /*
     * The initial state when there is no active room.
     */
    private void intializeUI() {
        connectActionFab.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.ic_video_call_white_24dp));
        connectActionFab.show();
        connectActionFab.setOnClickListener(connectActionClickListener());
        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());

        refreshRoomActionFab.show();
        refreshRoomActionFab.setOnClickListener(refreshRoomClickListener());


        refreshTrackActionFab.show();
        refreshTrackActionFab.setOnClickListener(refreshCamereaClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener());
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
    }

    /*
     * Show the current available audio devices.
     */
    private void showAudioDevices() {
        AudioDevice selectedDevice = audioSwitch.getSelectedAudioDevice();
        List<AudioDevice> availableAudioDevices = audioSwitch.getAvailableAudioDevices();

        if (selectedDevice != null) {
            int selectedDeviceIndex = availableAudioDevices.indexOf(selectedDevice);

            ArrayList<String> audioDeviceNames = new ArrayList<>();
            for (AudioDevice a : availableAudioDevices) {
                audioDeviceNames.add(a.getName());
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.room_screen_select_device)
                    .setSingleChoiceItems(
                            audioDeviceNames.toArray(new CharSequence[0]),
                            selectedDeviceIndex,
                            (dialog, index) -> {
                                dialog.dismiss();
                                AudioDevice selectedAudioDevice = availableAudioDevices.get(index);
                                updateAudioDeviceIcon(selectedAudioDevice);
                                audioSwitch.selectDevice(selectedAudioDevice);
                            })
                    .create()
                    .show();
        }
    }

    /*
     * Update the menu icon based on the currently selected audio device.
     */
    private void updateAudioDeviceIcon(AudioDevice selectedAudioDevice) {
        if (null != audioDeviceMenuItem) {
            int audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp;

            if (selectedAudioDevice instanceof BluetoothHeadset) {
                audioDeviceMenuIcon = R.drawable.ic_bluetooth_white_24dp;
            } else if (selectedAudioDevice instanceof WiredHeadset) {
                audioDeviceMenuIcon = R.drawable.ic_headset_mic_white_24dp;
            } else if (selectedAudioDevice instanceof Earpiece) {
                audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp;
            } else if (selectedAudioDevice instanceof Speakerphone) {
                audioDeviceMenuIcon = R.drawable.ic_volume_up_white_24dp;
            }
            audioDeviceMenuItem.setIcon(audioDeviceMenuIcon);
        }
    }

    /*
     * Get the preferred audio codec from shared preferences
     */
    private AudioCodec getAudioCodecPreference(String key, String defaultValue) {
        final String audioCodecName = preferences.getString(key, defaultValue);

        switch (audioCodecName) {
            case IsacCodec.NAME:
                return new IsacCodec();
            case OpusCodec.NAME:
                return new OpusCodec();
            case PcmaCodec.NAME:
                return new PcmaCodec();
            case PcmuCodec.NAME:
                return new PcmuCodec();
            case G722Codec.NAME:
                return new G722Codec();
            default:
                return new OpusCodec();
        }
    }

    /*
     * Get the preferred video codec from shared preferences
     */
    private VideoCodec getVideoCodecPreference(String key, String defaultValue) {
        final String videoCodecName = preferences.getString(key, defaultValue);

        switch (videoCodecName) {
            case Vp8Codec.NAME:
                boolean simulcast =
                        preferences.getBoolean(
                                SettingsActivity.PREF_VP8_SIMULCAST,
                                SettingsActivity.PREF_VP8_SIMULCAST_DEFAULT);
                return new Vp8Codec(simulcast);
            case H264Codec.NAME:
                return new H264Codec();
            case Vp9Codec.NAME:
                return new Vp9Codec();
            default:
                return new Vp8Codec();
        }
    }

    private boolean getAutomaticSubscriptionPreference(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    private EncodingParameters getEncodingParameters() {
        final int maxAudioBitrate =
                Integer.parseInt(
                        preferences.getString(
                                SettingsActivity.PREF_SENDER_MAX_AUDIO_BITRATE,
                                SettingsActivity.PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT));
        final int maxVideoBitrate =
                Integer.parseInt(
                        preferences.getString(
                                SettingsActivity.PREF_SENDER_MAX_VIDEO_BITRATE,
                                SettingsActivity.PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT));

        return new EncodingParameters(maxAudioBitrate, maxVideoBitrate);
    }

    /*
     * The actions performed during disconnect.
     */
    private void setDisconnectAction() {
        connectActionFab.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }

    /*
     * Creates an connect UI dialog
     */
    private void showConnectDialog() {
        System.out.println("--------showConnectDialog");
        EditText roomEditText = new EditText(this);
        connectDialog =
                Dialog.createConnectDialog(
                        roomEditText,
                        connectClickListener(roomEditText),
                        cancelConnectDialogClickListener(),
                        this);
        connectDialog.show();
    }

    /*
     * Called when remote participant joins the room
     */
    @SuppressLint("SetTextI18n")
    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Snackbar.make(
                            connectActionFab,
                            "Multiple participants are not currently support in this UI",
                            Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .show();
            return;
        }
        remoteParticipantIdentity = remoteParticipant.getIdentity();

        /*
         * Add remote participant renderer
         */
        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        /*
         * Start listening for participant events
         */
        remoteParticipant.setListener(remoteParticipantListener());
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        moveLocalVideoToThumbnailView();
        primaryVideoView.setMirror(false);
        videoTrack.addSink(primaryVideoView);
    }

    private void moveLocalVideoToThumbnailView() {
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);
            localVideoTrack.removeSink(primaryVideoView);
            localVideoTrack.addSink(thumbnailVideoView);
            localVideoView = thumbnailVideoView;
            thumbnailVideoView.setMirror(
                    cameraCapturerCompat.getCameraSource()
                            == CameraCapturerCompat.Source.FRONT_CAMERA);
        }
    }

    /*
     * Called when remote participant leaves the room
     */
    @SuppressLint("SetTextI18n")
    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        if (!remoteParticipant.getIdentity().equals(remoteParticipantIdentity)) {
            return;
        }

        /*
         * Remove remote participant renderer
         */
        if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Remove video only if subscribed to participant track
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }
        moveLocalVideoToPrimaryView();
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        videoTrack.removeSink(primaryVideoView);
    }

    private void moveLocalVideoToPrimaryView() {
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            thumbnailVideoView.setVisibility(View.GONE);
            if (localVideoTrack != null) {
                localVideoTrack.removeSink(thumbnailVideoView);
                localVideoTrack.addSink(primaryVideoView);
            }
            localVideoView = primaryVideoView;
            primaryVideoView.setMirror(
                    cameraCapturerCompat.getCameraSource()
                            == CameraCapturerCompat.Source.FRONT_CAMERA);
        }
    }

    /*
     * Room events listener
     */
    @SuppressLint("SetTextI18n")
    private Room.Listener roomListener() {

    System.out.println("--------roomListener");
    return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                System.out.println("--------onConnected");
                localParticipant = room.getLocalParticipant();



                setTitle(room.getName());

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                    break;
                }
                setPriorityTimer();
                setPriorityXXXX();

            }

            @Override
            public void onReconnecting(
                    @NonNull Room room, @NonNull TwilioException twilioException) {
                reconnectingProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                reconnectingProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {

                System.out.println("--------onConnectFailu1re");
                System.out.println("--------onConnectFailur2e");
                System.out.println("--------onConnectFailure3");
                System.out.println("--------onConnectFailure4");
                System.out.println("--------onConnectFailure5");
                System.out.println("--------onConnectFailure6");
                System.out.println("--------onConnectFailure7");
                System.out.println("--------onConnectFailure8");
                System.out.println("--------onConnectFailure9");
                System.out.println("--------onConnectFailure");
                System.out.println("--------onConnectFailure");
                System.out.println("--------onConnectFailure");
                System.out.println("--------onConnectFailure");
                System.out.println("--------onConnectFailure");
                System.out.println("ddd");
                System.out.println(room);
                System.out.println(e);
                audioSwitch.deactivate();
                intializeUI();
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                System.out.println("--------onDisconnected");
                localParticipant = null;
                reconnectingProgressBar.setVisibility(View.GONE);
                VideoActivity.this.room = null;
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    audioSwitch.deactivate();
                    intializeUI();
                    moveLocalVideoToPrimaryView();
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                System.out.println("--------onParticipantConnected");
                addRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                removeRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onRecordingStarted(Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped");
            }
        };
    }

    @SuppressLint("SetTextI18n")
    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.i(
                        TAG,
                        String.format(
                                "onAudioTrackPublished: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteAudioTrackPublication: sid=%s, enabled=%b, "
                                        + "subscribed=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteAudioTrackPublication.getTrackSid(),
                                remoteAudioTrackPublication.isTrackEnabled(),
                                remoteAudioTrackPublication.isTrackSubscribed(),
                                remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackUnpublished(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.i(
                        TAG,
                        String.format(
                                "onAudioTrackUnpublished: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteAudioTrackPublication: sid=%s, enabled=%b, "
                                        + "subscribed=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteAudioTrackPublication.getTrackSid(),
                                remoteAudioTrackPublication.isTrackEnabled(),
                                remoteAudioTrackPublication.isTrackSubscribed(),
                                remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackPublished(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(
                        TAG,
                        String.format(
                                "onDataTrackPublished: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteDataTrackPublication: sid=%s, enabled=%b, "
                                        + "subscribed=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteDataTrackPublication.getTrackSid(),
                                remoteDataTrackPublication.isTrackEnabled(),
                                remoteDataTrackPublication.isTrackSubscribed(),
                                remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackUnpublished(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(
                        TAG,
                        String.format(
                                "onDataTrackUnpublished: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteDataTrackPublication: sid=%s, enabled=%b, "
                                        + "subscribed=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteDataTrackPublication.getTrackSid(),
                                remoteDataTrackPublication.isTrackEnabled(),
                                remoteDataTrackPublication.isTrackSubscribed(),
                                remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackPublished(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.i(
                        TAG,
                        String.format(
                                "onVideoTrackPublished: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteVideoTrackPublication: sid=%s, enabled=%b, "
                                        + "subscribed=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteVideoTrackPublication.getTrackSid(),
                                remoteVideoTrackPublication.isTrackEnabled(),
                                remoteVideoTrackPublication.isTrackSubscribed(),
                                remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackUnpublished(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.i(
                        TAG,
                        String.format(
                                "onVideoTrackUnpublished: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteVideoTrackPublication: sid=%s, enabled=%b, "
                                        + "subscribed=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteVideoTrackPublication.getTrackSid(),
                                remoteVideoTrackPublication.isTrackEnabled(),
                                remoteVideoTrackPublication.isTrackSubscribed(),
                                remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackSubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication,
                    RemoteAudioTrack remoteAudioTrack) {
                Log.i(
                        TAG,
                        String.format(
                                "onAudioTrackSubscribed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteAudioTrack.isEnabled(),
                                remoteAudioTrack.isPlaybackEnabled(),
                                remoteAudioTrack.getName()));
            }

            @Override
            public void onAudioTrackUnsubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication,
                    RemoteAudioTrack remoteAudioTrack) {
                Log.i(
                        TAG,
                        String.format(
                                "onAudioTrackUnsubscribed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteAudioTrack.isEnabled(),
                                remoteAudioTrack.isPlaybackEnabled(),
                                remoteAudioTrack.getName()));
            }

            @Override
            public void onAudioTrackSubscriptionFailed(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication,
                    TwilioException twilioException) {
                Log.i(
                        TAG,
                        String.format(
                                "onAudioTrackSubscriptionFailed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteAudioTrackPublication: sid=%b, name=%s]"
                                        + "[TwilioException: code=%d, message=%s]",
                                remoteParticipant.getIdentity(),
                                remoteAudioTrackPublication.getTrackSid(),
                                remoteAudioTrackPublication.getTrackName(),
                                twilioException.getCode(),
                                twilioException.getMessage()));
            }

            @Override
            public void onDataTrackSubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication,
                    RemoteDataTrack remoteDataTrack) {
                Log.i(
                        TAG,
                        String.format(
                                "onDataTrackSubscribed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteDataTrack: enabled=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteDataTrack.isEnabled(),
                                remoteDataTrack.getName()));
            }

            @Override
            public void onDataTrackUnsubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication,
                    RemoteDataTrack remoteDataTrack) {
                Log.i(
                        TAG,
                        String.format(
                                "onDataTrackUnsubscribed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteDataTrack: enabled=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteDataTrack.isEnabled(),
                                remoteDataTrack.getName()));
            }

            @Override
            public void onDataTrackSubscriptionFailed(
                    RemoteParticipant remoteParticipant,
                    RemoteDataTrackPublication remoteDataTrackPublication,
                    TwilioException twilioException) {
                Log.i(
                        TAG,
                        String.format(
                                "onDataTrackSubscriptionFailed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteDataTrackPublication: sid=%b, name=%s]"
                                        + "[TwilioException: code=%d, message=%s]",
                                remoteParticipant.getIdentity(),
                                remoteDataTrackPublication.getTrackSid(),
                                remoteDataTrackPublication.getTrackName(),
                                twilioException.getCode(),
                                twilioException.getMessage()));
            }

            @Override
            public void onVideoTrackSubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication,
                    RemoteVideoTrack remoteVideoTrack) {
                Log.i(
                        TAG,
                        String.format(
                                "onVideoTrackSubscribed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteVideoTrack: enabled=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteVideoTrack.isEnabled(),
                                remoteVideoTrack.getName()));

   // updated part


               remoteVideoTrack.setContentPreferences(new VideoContentPreferences(new VideoDimensions(1280, 720)));
                addRemoteParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackUnsubscribed(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication,
                    RemoteVideoTrack remoteVideoTrack) {
                Log.i(
                        TAG,
                        String.format(
                                "onVideoTrackUnsubscribed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteVideoTrack: enabled=%b, name=%s]",
                                remoteParticipant.getIdentity(),
                                remoteVideoTrack.isEnabled(),
                                remoteVideoTrack.getName()));
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication,
                    TwilioException twilioException) {
                Log.i(
                        TAG,
                        String.format(
                                "onVideoTrackSubscriptionFailed: "
                                        + "[RemoteParticipant: identity=%s], "
                                        + "[RemoteVideoTrackPublication: sid=%b, name=%s]"
                                        + "[TwilioException: code=%d, message=%s]",
                                remoteParticipant.getIdentity(),
                                remoteVideoTrackPublication.getTrackSid(),
                                remoteVideoTrackPublication.getTrackName(),
                                twilioException.getCode(),
                                twilioException.getMessage()));
                Snackbar.make(
                                connectActionFab,
                                String.format(
                                        "Failed to subscribe to %s video track",
                                        remoteParticipant.getIdentity()),
                                Snackbar.LENGTH_LONG)
                        .show();
            }

            @Override
            public void onAudioTrackEnabled(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {}

            @Override
            public void onAudioTrackDisabled(
                    RemoteParticipant remoteParticipant,
                    RemoteAudioTrackPublication remoteAudioTrackPublication) {}

            @Override
            public void onVideoTrackEnabled(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {}

            @Override
            public void onVideoTrackDisabled(
                    RemoteParticipant remoteParticipant,
                    RemoteVideoTrackPublication remoteVideoTrackPublication) {}
        };
    }


    private DialogInterface.OnClickListener connectClickListener(final EditText roomEditText) {
        System.out.println("--------OnClickListener");
        return (dialog, which) -> {
            /*
             * Connect to room
             */
            connectToRoom(roomEditText.getText().toString());
        };
    }



    private View.OnClickListener disconnectClickListener() {
        return v -> {
            /*
             * Disconnect from room
             */
            if (room != null) {
                room.disconnect();
            }
            intializeUI();
        };
    }

    private View.OnClickListener connectActionClickListener() {
        return v -> showConnectDialog();
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return (dialog, which) -> {
            intializeUI();
            connectDialog.dismiss();
        };
    }




    private View.OnClickListener refreshRoomClickListener(){
        isRefresh = true;
        System.out.println("<<<<<<<<<<<<<<<<<refreshRoomClickListener");
        return v -> {

            if (room != null) {
                room.disconnect();
            }
            if(roomNameStored != null){
                connectToRoom(roomNameStored);
            }else{
                connectToRoom("aaa");
            }
        };
    }





    private View.OnClickListener refreshCamereaClickListener() {

        isRefresh = true;
        return v -> {
            onPause();
            onResume();


        };
    }


    private View.OnClickListener switchCameraClickListener() {

        // camera in out switch

        System.out.println("<<<<<<<<<<<switchCameraClickListener>>>>>>>>>>>>>>>>>");

        return v -> {
            if (cameraCapturerCompat != null) {
                CameraCapturerCompat.Source cameraSource = cameraCapturerCompat.getCameraSource();



                cameraCapturerCompat.switchCamera();
                if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                    thumbnailVideoView.setMirror(
                            cameraSource == CameraCapturerCompat.Source.BACK_CAMERA);
                } else {
                    primaryVideoView.setMirror(
                            cameraSource == CameraCapturerCompat.Source.BACK_CAMERA);
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return v -> {
            /*
             * Enable/disable the local video track
             */
            if (localVideoTrack != null) {
                boolean enable = !localVideoTrack.isEnabled();
                localVideoTrack.enable(enable);
                int icon;
                if (enable) {
                    icon = R.drawable.ic_videocam_white_24dp;
                    switchCameraActionFab.show();
                } else {
                    icon = R.drawable.ic_videocam_off_black_24dp;
                    switchCameraActionFab.hide();
                }
                localVideoActionFab.setImageDrawable(
                        ContextCompat.getDrawable(VideoActivity.this, icon));
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return v -> {
            /*
             * Enable/disable the local audio track. The results of this operation are
             * signaled to other Participants in the same Room. When an audio track is
             * disabled, the audio is muted.
             */
            if (localAudioTrack != null) {
                boolean enable = !localAudioTrack.isEnabled();
                localAudioTrack.enable(enable);
                int icon = enable ? R.drawable.ic_mic_white_24dp : R.drawable.ic_mic_off_black_24dp;
                muteActionFab.setImageDrawable(ContextCompat.getDrawable(VideoActivity.this, icon));
            }
        };
    }

    private void retrieveAccessTokenfromServer() {
        System.out.println("-------------- retrieveAccessTokenfromServer");
        System.out.println(ACCESS_TOKEN_SERVER);
        Ion.with(this)
                .load(
                        String.format(
                                "%s?identity=%s",
                                ACCESS_TOKEN_SERVER, UUID.randomUUID().toString()))
                .asString()
                .setCallback(
                        (e, token) -> {
                            System.out.println("-------------- retrieveAccessTokenfromServer token");
                            System.out.println(token);
                            if (e == null) {
                                VideoActivity.this.accessToken = token;
                            } else {
                                Toast.makeText(
                                                VideoActivity.this,
                                                R.string.error_retrieving_access_token,
                                                Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
    }
}
