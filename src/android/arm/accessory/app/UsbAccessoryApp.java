/*
 * This file is part of the Android Open Accessory (AOA) over USB example
 * program to support AOA1.0 and AOA2.0 on Linux/Android, ARM-based platforms
 *
 * Copyright (C) 2012 Jesse Dannenbring <jdannenbring@adeneo-embedded.com>
 *
 * This program implements basic AOA1.0 custom command/control processing.
 * Sends currently playing music metadata, and allows control of picture slideshow
 * on ADK device (see the corresponding project "android-arm-accessory".
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package android.arm.accessory.app;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

import android.arm.accessory.app.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.hardware.usb.*;

public class UsbAccessoryApp extends Activity {
	private UsbAccessory mAccessory = null;
	private Button mBtSend = null;
	private FileOutputStream mFout = null;
	
	private Button prev = null;
	private Button next = null;
	
	/* 
	 * These intents are not documented, so I took them from
	 * ${androidsources}/packages/apps/Music/src/com/android/music/MediaPlaybackService.java:
	 * 
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Integer: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "track" - String: the name of the track
     * The intent has an action that is one of
     * "com.android.music.metachanged"
     * "com.android.music.queuechanged",
     * "com.android.music.playbackcomplete"
     * "com.android.music.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
	public static final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    public static final String META_CHANGED = "com.android.music.metachanged";
    public static final String QUEUE_CHANGED = "com.android.music.queuechanged";
    public static final String PLAYBACK_COMPLETE = "com.android.music.playbackcomplete";

    public static final String SERVICECMD = "com.android.music.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";

    public static final String TOGGLEPAUSE_ACTION = "com.android.music.musicservicecommand.togglepause";
    public static final String PAUSE_ACTION = "com.android.music.musicservicecommand.pause";
    public static final String PREVIOUS_ACTION = "com.android.music.musicservicecommand.previous";
    public static final String NEXT_ACTION = "com.android.music.musicservicecommand.next";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        /* register receiver to obtain song metadata from music player */
        IntentFilter iF = new IntentFilter();
        iF.addAction(META_CHANGED);
        iF.addAction(PLAYSTATE_CHANGED);
        iF.addAction(PLAYBACK_COMPLETE);
        iF.addAction(QUEUE_CHANGED);
        registerReceiver(mReceiver, iF);
        
        /* 
         * Two ways the Activity is properly started: 
         * A) An USB Accessory was just attached that matches our intent filter.
         * B) An USB Accessory is already attached
         */
        if(getIntent().getAction().equals("android.hardware.usb.action.USB_ACCESSORY_ATTACHED")){
        	log("USB Accessory attached");
        	UsbAccessory accessory = (UsbAccessory) getIntent().getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        	mAccessory = accessory;
        } else {
        	log("Application searching for an Accessory");
        	UsbAccessory[] accessories = ((UsbManager) getSystemService(Context.USB_SERVICE)).getAccessoryList();
        	if (accessories == null) {
        		log("Application cannot find an accessory");
	        	finish();
        	}
			for (UsbAccessory accessory : accessories) {
				if (accessory.getManufacturer().equals("Freescale")) {
					if (accessory.getModel().equals("iMX6Q")) {
        				if (accessory.getVersion().equals("SabreLite")) {
        					mAccessory = accessory;
        					break;
        				}
        			}
        		}
        		log("Application cannot find an accessory");
	        	finish();
        	}
        }
        
        FileDescriptor fd = null;
        try {
			fd = ((UsbManager) getSystemService(Context.USB_SERVICE)).openAccessory(mAccessory).getFileDescriptor();
		} catch (IllegalArgumentException e) {
			finish();
		} catch (NullPointerException e) {
			finish();
		}
    	mFout = new FileOutputStream(fd);
    	
        mBtSend = (Button)(findViewById(R.id.music_player));
        mBtSend.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				/* start the android music application */
				Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
				startActivity(intent);
			}
		});
        
        prev = (Button) (findViewById(R.id.prev));
        prev.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				sendCmd("prev");
			}
		});
        
        next = (Button) (findViewById(R.id.next));
        next.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				sendCmd("next");
			}
		});
    }
    
    /* Sends ADK specific commands and data */
	public void sendCmd(final String data) {
		if (mAccessory == null) {
			return;
		}
    	new Thread(new Runnable() {
			public void run() {
				try {
					log("Writing data: " + data);
					mFout.write(data.getBytes());
					log("Done writing");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
    }
    
	private void log(String l) {
		Log.d("UsbAccessoryApp", l);
	}
	
	/* Used to determine current song information from Music player */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
    	
    	@Override
    	public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			log("Music Player: " + action);
			String artist = intent.getStringExtra("artist");
			String album = intent.getStringExtra("album");
			String track = intent.getStringExtra("track");
			Boolean isPlaying = intent.getBooleanExtra("playing", false);
			
			/* send the music metadata information to the Accessory */
			String accessory_cmd = action + "/" + artist + "/" + album + "/" + track + "/" + isPlaying;
			log("Song Metadata: " + accessory_cmd);
			sendCmd(accessory_cmd);
    	}
    };
}
