/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.stage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.widget.Toast;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.ble.SensorInfo;
import org.catrobat.catroid.bluetooth.BluetoothManager;
import org.catrobat.catroid.bluetooth.DeviceListActivity;
import org.catrobat.catroid.common.Constants;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.actions.MonitorSensorAction;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.legonxt.LegoNXT;
import org.catrobat.catroid.legonxt.LegoNXTBtCommunicator;
import org.catrobat.catroid.ui.dialogs.CustomAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

@SuppressLint("NewApi")
@SuppressWarnings("deprecation")
public class PreStageActivity extends Activity {
	private static final String TAG = PreStageActivity.class.getSimpleName();

	private static final int REQUEST_ENABLE_BLUETOOTH = 2000;
	private static final int REQUEST_CONNECT_DEVICE = 1000;
	public static final int REQUEST_RESOURCES_INIT = 101;
	public static final int REQUEST_TEXT_TO_SPEECH = 10;
	public static BluetoothDevice sensorTag;
	public static BluetoothGatt bg;
	private int requiredResourceCounter;
	private static LegoNXT legoNXT;
	private static ProgressDialog connectingProgressDialog;
	private static TextToSpeech textToSpeech;
	private static OnUtteranceCompletedListenerContainer onUtteranceCompletedListenerContainer;

	private boolean autoConnect = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int requiredResources = getRequiredRessources();
		requiredResourceCounter = Integer.bitCount(requiredResources);

		if ((requiredResources & Brick.TEXT_TO_SPEECH) > 0) {
			Intent checkIntent = new Intent();
			checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
			startActivityForResult(checkIntent, REQUEST_TEXT_TO_SPEECH);
		}
		if ((requiredResources & Brick.BLUETOOTH_BLE_SENSORS) > 0) {
			BluetoothManager bluetoothManager = new BluetoothManager(this);

			int bluetoothState = bluetoothManager.activateBluetooth();
			if (bluetoothState == BluetoothManager.BLUETOOTH_NOT_SUPPORTED) {

				Toast.makeText(PreStageActivity.this, R.string.notification_blueth_err, Toast.LENGTH_LONG).show();
				resourceFailed();
			} else {
				if (bluetoothState == BluetoothManager.BLUETOOTH_ALREADY_ON) {
					startBluetoothCommunication(true);
				}
				resourceInitialized();
			}
		}
		if (requiredResourceCounter == Brick.NO_RESOURCES) {
			startStage();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (requiredResourceCounter == 0) {
			finish();
		}
	}

	//all resources that should be reinitialized with every stage start
	public static void shutdownResources() {
		if (textToSpeech != null) {
			textToSpeech.stop();
			textToSpeech.shutdown();
		}
		if (legoNXT != null) {
			legoNXT.pauseCommunicator();
		}
	}

	//all resources that should not have to be reinitialized every stage start
	public static void shutdownPersistentResources() {
		if (legoNXT != null) {
			legoNXT.destroyCommunicator();
			legoNXT = null;
		}
		deleteSpeechFiles();
	}

	private static void deleteSpeechFiles() {
		File pathToSpeechFiles = new File(Constants.TEXT_TO_SPEECH_TMP_PATH);
		if (pathToSpeechFiles.isDirectory()) {
			for (File file : pathToSpeechFiles.listFiles()) {
				file.delete();
			}
		}
	}

	private void resourceFailed() {
		setResult(RESULT_CANCELED, getIntent());
		finish();
	}

	private synchronized void resourceInitialized() {
		//Log.i("res", "Resource initialized: " + requiredResourceCounter);

		requiredResourceCounter--;
		if (requiredResourceCounter == 0) {
			startStage();
		}
	}

	public void startStage() {
		setResult(RESULT_OK, getIntent());
		finish();
	}

	private void startBluetoothCommunication(boolean autoConnect) {
		connectingProgressDialog = ProgressDialog.show(this, "",
				getResources().getString(R.string.connecting_please_wait), true);
		Intent serverIntent = new Intent(this, DeviceListActivity.class);
		serverIntent.putExtra(DeviceListActivity.AUTO_CONNECT, autoConnect);
		this.startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	}

	private int getRequiredRessources() {
		ArrayList<Sprite> spriteList = (ArrayList<Sprite>) ProjectManager.getInstance().getCurrentProject()
				.getSpriteList();

		int ressources = Brick.NO_RESOURCES;
		for (Sprite sprite : spriteList) {
			ressources |= sprite.getRequiredResources();
		}
		return ressources;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i("bt", "requestcode " + requestCode + " result code" + resultCode);

		switch (requestCode) {
			case REQUEST_ENABLE_BLUETOOTH:
				switch (resultCode) {
					case Activity.RESULT_OK:
						startBluetoothCommunication(true);
						break;
					case Activity.RESULT_CANCELED:
						Toast.makeText(PreStageActivity.this, R.string.notification_blueth_err, Toast.LENGTH_LONG)
								.show();
						resourceFailed();
						break;
				}
				break;

			case REQUEST_CONNECT_DEVICE:
				switch (resultCode) {
					case Activity.RESULT_OK:
						/*
						 * legoNXT = new LegoNXT(this, recieveHandler);
						 * String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
						 * autoConnect = data.getExtras().getBoolean(DeviceListActivity.AUTO_CONNECT);
						 * legoNXT.startBTCommunicator(address);
						 */
						if (bg == null) {
							bg = sensorTag.connectGatt(getBaseContext(), false, mGattCallback);
						}
						break;

					case Activity.RESULT_CANCELED:
						connectingProgressDialog.dismiss();
						Toast.makeText(PreStageActivity.this, R.string.bt_connection_failed, Toast.LENGTH_LONG).show();
						resourceFailed();
						break;
				}
				break;

			case REQUEST_TEXT_TO_SPEECH:
				if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
					textToSpeech = new TextToSpeech(getApplicationContext(), new OnInitListener() {
						@Override
						public void onInit(int status) {
							onUtteranceCompletedListenerContainer = new OnUtteranceCompletedListenerContainer();
							textToSpeech.setOnUtteranceCompletedListener(onUtteranceCompletedListenerContainer);
							resourceInitialized();
							if (status == TextToSpeech.ERROR) {
								Toast.makeText(PreStageActivity.this,
										"Error occurred while initializing Text-To-Speech engine", Toast.LENGTH_LONG)
										.show();
								resourceFailed();
							}
						}
					});
					if (textToSpeech.isLanguageAvailable(Locale.getDefault()) == TextToSpeech.LANG_MISSING_DATA) {
						Intent installIntent = new Intent();
						installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
						startActivity(installIntent);
						resourceFailed();
					}
				} else {
					AlertDialog.Builder builder = new CustomAlertDialogBuilder(this);
					builder.setMessage(R.string.text_to_speech_engine_not_installed).setCancelable(false)
							.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int id) {
									Intent installIntent = new Intent();
									installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
									startActivity(installIntent);
									resourceFailed();
								}
							}).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int id) {
									dialog.cancel();
									resourceFailed();
								}
							});
					AlertDialog alert = builder.create();
					alert.show();
				}
				break;
			default:
				resourceFailed();
				break;
		}
	}

	public static void textToSpeech(String text, File speechFile, OnUtteranceCompletedListener listener,
			HashMap<String, String> speakParameter) {
		if (text == null) {
			text = "";
		}

		if (onUtteranceCompletedListenerContainer.addOnUtteranceCompletedListener(speechFile, listener,
				speakParameter.get(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))) {
			int status = textToSpeech.synthesizeToFile(text, speakParameter, speechFile.getAbsolutePath());
			if (status == TextToSpeech.ERROR) {
				Log.e(TAG, "File synthesizing failed");
			}
		}
	}

	//messages from Lego NXT device can be handled here
	// TODO should be fixed - could lead to problems
	@SuppressLint("HandlerLeak")
	final Handler recieveHandler = new Handler() {
		@Override
		public void handleMessage(Message myMessage) {

			Log.i("bt", "message" + myMessage.getData().getInt("message"));
			switch (myMessage.getData().getInt("message")) {
				case LegoNXTBtCommunicator.STATE_CONNECTED:
					//autoConnect = false;

					resourceInitialized();
					break;
				case LegoNXTBtCommunicator.STATE_CONNECTERROR:
					Toast.makeText(PreStageActivity.this, R.string.bt_connection_failed, Toast.LENGTH_SHORT).show();
					connectingProgressDialog.dismiss();
					legoNXT.destroyCommunicator();
					legoNXT = null;
					if (autoConnect) {
						startBluetoothCommunication(false);
					} else {
						resourceFailed();
					}
					break;
			}
		}
	};

	public BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		private int[] p_cals;

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			//Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
			if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
				/*
				 * Once successfully connected, we must next discover all the services on the
				 * device before we can read and write their characteristics.
				 */
				//Toast.makeText(getApplicationContext(), "Connected to SensorTag", Toast.LENGTH_SHORT).show();
				Log.d("dev", "Connected to SensorTag");
				gatt.discoverServices();

			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d("dev", "Services done");
				connectingProgressDialog.dismiss();
				startStage();
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			/*
			 * After notifications are enabled, all updates from the device on characteristic
			 * value changes will be posted here. Similar to read, we hand these up to the
			 * UI thread to update the display.
			 */
			if (MonitorSensorAction.PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
				Log.d("dev", "reading pressure");
				if (p_cals == null) {
					return;
				}
				//double pressure = SensorInfo.extractBarometer(characteristic, p_cals);
				double temp = SensorInfo.extractBarTemperature(characteristic, p_cals);

				String t = String.format("%.1f\u00B0C", temp);
				Log.d("dev", t.substring(0, 4));
				SensorInfo.Temp = Float.parseFloat(t.substring(0, 4));
			}
			if (MonitorSensorAction.PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
				p_cals = SensorInfo.extractCalibrationCoefficients(characteristic);
				Log.d("dev", "reading cals");
				//double pressure = SensorInfo.extractBarometer(characteristic, p_cals);
				double temp = SensorInfo.extractBarTemperature(characteristic, p_cals);

				String t = String.format("%.1f\u00B0C", temp);
				Log.d("dev", t.substring(0, 4));
				SensorInfo.Temp = Float.parseFloat(t.substring(0, 4));
			}
			if (MonitorSensorAction.ACC_DATA_CHAR.equals(characteristic.getUuid())) {
				float[] arr = SensorInfo.extractAccInfo(characteristic);
				SensorInfo.Acc_x = arr[0];
				SensorInfo.Acc_y = arr[1];
				SensorInfo.Acc_z = arr[2];
				Log.d("dev", "x=" + arr[0] + " y=" + arr[1] + " z=" + arr[2]);
			}
			if (MonitorSensorAction.GYRO_DATA_CHAR.equals(characteristic.getUuid())) {
				float[] arr = SensorInfo.extractGyroInfo(characteristic);
				SensorInfo.Gyro_x = arr[0];
				SensorInfo.Gyro_y = arr[1];
				SensorInfo.Gyro_z = arr[2];
				Log.d("dev", "gyro_x=" + arr[0] + " gyro_y=" + arr[1] + " gyro_z=" + arr[2]);
			}
			if (MonitorSensorAction.MAG_DATA_CHAR.equals(characteristic.getUuid())) {
				float[] arr = SensorInfo.extractMagInfo(characteristic);
				SensorInfo.Mag_x = arr[0];
				SensorInfo.Mag_y = arr[1];
				SensorInfo.Mag_z = arr[2];

				Log.d("dev", "mag_x=" + arr[0] + " mag_y=" + arr[1] + " mag_z=" + arr[2]);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (MonitorSensorAction.PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
				Log.d("dev", "reading pressure");
				if (p_cals == null) {
					return;
				}
				double pressure = SensorInfo.extractBarometer(characteristic, p_cals);
				double temp = SensorInfo.extractBarTemperature(characteristic, p_cals);
				String t = String.format("%.1f\u00B0C", temp);
				Log.d("dev", t.substring(0, 4));
				SensorInfo.Temp = Float.parseFloat(t.substring(0, 4));

			}
			if (MonitorSensorAction.PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
				p_cals = SensorInfo.extractCalibrationCoefficients(characteristic);
				Log.d("dev", "reading cals");
			}
			if (MonitorSensorAction.ACC_DATA_CHAR.equals(characteristic.getUuid())) {
				float[] arr = SensorInfo.extractAccInfo(characteristic);
				SensorInfo.Acc_x = arr[0];
				SensorInfo.Acc_y = arr[1];
				SensorInfo.Acc_z = arr[2];
				Log.d("dev", "x=" + arr[0] + " y=" + arr[1] + " z=" + arr[2]);
			}
			if (MonitorSensorAction.GYRO_DATA_CHAR.equals(characteristic.getUuid())) {
				float[] arr = SensorInfo.extractGyroInfo(characteristic);
				SensorInfo.Gyro_x = arr[0];
				SensorInfo.Gyro_y = arr[1];
				SensorInfo.Gyro_z = arr[2];
				Log.d("dev", "gyro_x=" + arr[0] + " gyro_y=" + arr[1] + " gyro_z=" + arr[2]);
			}
			if (MonitorSensorAction.MAG_DATA_CHAR.equals(characteristic.getUuid())) {
				float[] arr = SensorInfo.extractMagInfo(characteristic);
				SensorInfo.Mag_x = arr[0];
				SensorInfo.Mag_y = arr[1];
				SensorInfo.Mag_z = arr[2];

				Log.d("dev", "mag_x=" + arr[0] + " mag_y=" + arr[1] + " mag_z=" + arr[2]);
			}
			gatt.setCharacteristicNotification(characteristic, true);
			BluetoothGattDescriptor d = characteristic.getDescriptor(MonitorSensorAction.CONFIG_DESCRIPTOR);
			d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			gatt.writeDescriptor(d);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor d, int status) {
			if (d.getCharacteristic().equals(
					gatt.getService(MonitorSensorAction.PRESSURE_SERVICE).getCharacteristic(
							MonitorSensorAction.PRESSURE_CAL_CHAR))) {
				BluetoothGattCharacteristic c = gatt.getService(MonitorSensorAction.PRESSURE_SERVICE)
						.getCharacteristic(MonitorSensorAction.PRESSURE_CONFIG_CHAR);
				c.setValue(new byte[] { 0x01 });
				gatt.writeCharacteristic(c);
			} else {
				return;
			}
		}

		int i = 1;

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (MonitorSensorAction.PRESSURE_CONFIG_CHAR.equals(characteristic.getUuid())) {
				if (i == 1) {
					Log.d("dev", "cal enabled");
					i++;
					gatt.readCharacteristic(gatt.getService(MonitorSensorAction.PRESSURE_SERVICE).getCharacteristic(
							MonitorSensorAction.PRESSURE_CAL_CHAR));
				} else {
					Log.d("dev", "data enabled");
					gatt.readCharacteristic(gatt.getService(MonitorSensorAction.PRESSURE_SERVICE).getCharacteristic(
							MonitorSensorAction.PRESSURE_DATA_CHAR));
				}
			}
			if (MonitorSensorAction.ACC_CONFIG_CHAR.equals(characteristic.getUuid())) {
				Log.d("dev", "accelerometer enabled");
				gatt.readCharacteristic(gatt.getService(MonitorSensorAction.ACC_SERVICE).getCharacteristic(
						MonitorSensorAction.ACC_DATA_CHAR));
			}
			if (MonitorSensorAction.GYRO_CONFIG_CHAR.equals(characteristic.getUuid())) {
				Log.d("dev", "gyroscope enabled");
				gatt.readCharacteristic(gatt.getService(MonitorSensorAction.GYRO_SERVICE).getCharacteristic(
						MonitorSensorAction.GYRO_DATA_CHAR));
			}
			if (MonitorSensorAction.MAG_CONFIG_CHAR.equals(characteristic.getUuid())) {
				Log.d("dev", "magnetometer enabled");
				gatt.readCharacteristic(gatt.getService(MonitorSensorAction.MAG_SERVICE).getCharacteristic(
						MonitorSensorAction.MAG_DATA_CHAR));
			}
		}
	};
}