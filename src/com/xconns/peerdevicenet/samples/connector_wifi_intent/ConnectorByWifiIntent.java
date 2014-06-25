package com.xconns.peerdevicenet.samples.connector_wifi_intent;

import java.util.HashMap;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.RouterConnectionClient;
import com.xconns.peerdevicenet.core.RouterService;
import com.xconns.peerdevicenet.samples.connector_wifi_intent.R;

public class ConnectorByWifiIntent extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connector_by_wifi_intent);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	/**
	 * main fragment containing all GUI.
	 */
	public static class PlaceholderFragment extends Fragment {

		private static final String TAG = "ByeBye";

		private Activity activity = null;

		private TextView mNetMsg = null;
		private Button mConnButton = null;
		private Button mDoneButton = null;

		private CharSequence setupNetText = null;
		private CharSequence stopSearchText = null;
		private CharSequence searchConnectText = null;
		private CharSequence onNetText = null;
		private CharSequence missNetText = null;
		
		private ArrayAdapter<String> mPeerListAdapter;
		private ListView mPeerListView;

		private DeviceInfo mDevice = null; // my own device info
		private NetInfo mNet = null; // network my device connect to
		// peer connection parameters
		private String securityToken = ""; // dont check conn security token
		private int connTimeout = 5000; // 5 seconds for socket conn timeout
		private int searchTimeout = 30000; // 30 seconds timeout for searching											// peers

		public PlaceholderFragment() {
		}

		@Override
		public void onAttach(Activity act) {
			// TODO Auto-generated method stub
			super.onAttach(activity);
			activity = act;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_connector_by_wifi_intent,
					container, false);

			setupNetText = getResources().getText(R.string.setup_net);
			stopSearchText = getResources().getText(R.string.stop_search);
			searchConnectText = getResources().getText(R.string.search_connect);
			onNetText = getResources().getText(R.string.on_net);
			missNetText = getResources().getText(R.string.miss_net);

			mNetMsg = (TextView) rootView.findViewById(R.id.net_msg);
			mConnButton = (Button) rootView.findViewById(R.id.button_conn);
			mConnButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (mNet == null) {
						configWifi();
					} else {
						Log.d(TAG, "start peer search");
						Intent in = new Intent(Router.ACTION_START_SEARCH);
						in.putExtra(Router.SEARCH_TIMEOUT, searchTimeout);
						activity.startService(in);
					}
				}
			});
			mDoneButton = (Button) rootView.findViewById(R.id.button_done);
			mDoneButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					//shutdon router by calling stopService
					Intent intent = new Intent(Router.ACTION_ROUTER_SHUTDOWN);
					activity.stopService(intent);
						//
					activity.finish();
				}
			});
			
			// Initialize the array adapter for the conversation thread
			mPeerListAdapter = new ArrayAdapter<String>(activity, R.layout.peer_name);
			mPeerListView = (ListView) rootView.findViewById(R.id.peers_list);
			mPeerListView.setAdapter(mPeerListAdapter);
			
			//start router service by intent
			//so it can keep running at background
			//even when client unbind
			//must be stopped by calling stopService after all clients unbind
			Intent intent = new Intent(Router.ACTION_CONNECTION_SERVICE);
			activity.startService(intent);

			//register recvers to handle ConnectionService related broadcast intents
			IntentFilter filter = new IntentFilter();
			filter.addAction(Router.ACTION_GET_NETWORKS);
			filter.addAction(Router.ACTION_GET_ACTIVE_NETWORK);
			filter.addAction(Router.ACTION_ACTIVATE_NETWORK);
			filter.addAction(Router.ACTION_NETWORK_CONNECTED);
			filter.addAction(Router.ACTION_NETWORK_DISCONNECTED);
			filter.addAction(Router.ACTION_SEARCH_START);
			filter.addAction(Router.ACTION_SEARCH_FOUND_DEVICE);
			filter.addAction(Router.ACTION_SEARCH_COMPLETE);
			filter.addAction(Router.ACTION_CONNECTING);
			filter.addAction(Router.ACTION_CONNECTION_FAILED);
			filter.addAction(Router.ACTION_CONNECTED);
			filter.addAction(Router.ACTION_DISCONNECTED);
			filter.addAction(Router.ACTION_SET_CONNECTION_INFO);
			filter.addAction(Router.ACTION_GET_CONNECTION_INFO);
			filter.addAction(Router.ACTION_GET_DEVICE_INFO);
			activity.registerReceiver(mReceiver, filter);

			// setup my device name known to peers
			String myDeviceName = android.os.Build.MODEL;
			if (myDeviceName == null || myDeviceName.length() == 0) {
				myDeviceName = "MyDeviceName";
			}
			Intent in = new Intent(Router.ACTION_SET_CONNECTION_INFO);
			in.putExtra(Router.DEVICE_NAME, myDeviceName);
			in.putExtra(Router.USE_SSL, false);
			activity.startService(in);

			// start by checking if device is connected to any networks
			in = new Intent(Router.ACTION_GET_NETWORKS);
			activity.startService(in);

			return rootView;
		}

		@Override
		public void onResume() {
			// TODO Auto-generated method stub
			super.onResume();

		}

		@Override
		public void onDestroy() {
			// TODO Auto-generated method stub
			super.onDestroy();
			Log.d(TAG, "Connector destroyed, unregister broadcast recver");
			activity.unregisterReceiver(mReceiver);
			// don't stop RouterService here
		}

		private void configWifi() {
			Intent in = new Intent(Settings.ACTION_WIFI_SETTINGS);
			activity.startActivity(in);
		}

		private void updateGuiNoNet() {
			mNetMsg.setText(missNetText);
			mConnButton.setText(setupNetText);
		}

		private void updateGuiOnNet(NetInfo net) {
			mNetMsg.setText(onNetText+": "+net.name+", start searching and connecting to peer devices");
			mConnButton.setText(searchConnectText);
		}

		private void updateGuiSearchStart() {
			mConnButton.setText(stopSearchText);
		}

		private void updateGuiSearchComplete() {
			mConnButton.setText(searchConnectText);
		}
		
		private void addDeviceToList(DeviceInfo dev) {
			mPeerListAdapter.add(dev.name+" : "+dev.addr);
			mPeerListAdapter.notifyDataSetChanged();
		}

		private void delDeviceFromList(DeviceInfo dev) {
			mPeerListAdapter.remove(dev.name+" : "+dev.addr);
			mPeerListAdapter.notifyDataSetChanged();
		}

		private BroadcastReceiver mReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				DeviceInfo device = null;
				NetInfo net = null;
				String pname = null;
				String paddr = null;
				String pport = null;

				final String action = intent.getAction();
				if (Router.ACTION_SEARCH_FOUND_DEVICE.equals(action)) {
				// handle msgs for scan
					pname = intent.getStringExtra(Router.PEER_NAME);
					paddr = intent.getStringExtra(Router.PEER_ADDR);
					pport = intent.getStringExtra(Router.PEER_PORT);
					boolean uSSL = intent.getBooleanExtra(Router.USE_SSL, false);
					device = new DeviceInfo(pname,paddr,pport);
					Log.d(TAG, "onSearchFoundDevice: " + device);

					// after find devices
					// auto connect to them
					// connect from device with small ip to device with large ip
					if (mDevice.addr.compareTo(device.addr) < 0) {
						Log.d(TAG, "connect to client: " + device.addr);
						Intent in = new Intent(Router.ACTION_CONNECT);
						in.putExtra(Router.PEER_NAME, device.name);
						in.putExtra(Router.PEER_ADDR, device.addr);
						in.putExtra(Router.PEER_PORT, device.port);
						in.putExtra(Router.AUTHENTICATION_TOKEN, securityToken.getBytes());
						in.putExtra(Router.CONNECT_TIMEOUT, connTimeout);
						activity.startService(in);
					}

				} else if (Router.ACTION_SEARCH_COMPLETE.equals(action)) {
					Log.d(TAG, "search complete");
					updateGuiSearchComplete();
				} else if (Router.ACTION_SEARCH_START.equals(action)) {
					updateGuiSearchStart();
				} else if (Router.ACTION_CONNECTED.equals(action)) {
					pname = intent.getStringExtra(Router.PEER_NAME);
					paddr = intent.getStringExtra(Router.PEER_ADDR);
					pport = intent.getStringExtra(Router.PEER_PORT);
					device = new DeviceInfo(pname,paddr,pport);
					addDeviceToList(device);
					Log.d(TAG, "a device connected");
				} else if (Router.ACTION_DISCONNECTED.equals(action)) {
					pname = intent.getStringExtra(Router.PEER_NAME);
					paddr = intent.getStringExtra(Router.PEER_ADDR);
					pport = intent.getStringExtra(Router.PEER_PORT);
					device = new DeviceInfo(pname,paddr,pport);
					delDeviceFromList(device);
					Log.d(TAG, "a device disconnected: " + device.addr);
				} else if (Router.ACTION_GET_CONNECTED_PEERS.equals(action)) {
					String[] names = intent.getStringArrayExtra(Router.PEER_NAMES);
					String[] addrs = intent.getStringArrayExtra(Router.PEER_ADDRS);
					String[] ports = intent.getStringArrayExtra(Router.PEER_PORTS);
					for(int i=0;i<names.length;i++) {
						addDeviceToList(new DeviceInfo(names[i], addrs[i], ports[1]));
					}
					Log.d(TAG, "get_connected_peers");
				} else if (Router.ACTION_CONNECTING.equals(action)) {
					pname = intent.getStringExtra(Router.PEER_NAME);
					paddr = intent.getStringExtra(Router.PEER_ADDR);
					pport = intent.getStringExtra(Router.PEER_PORT);
					device = new DeviceInfo(pname,paddr,pport);
					byte[] token = intent.getByteArrayExtra(Router.AUTHENTICATION_TOKEN);
					Log.d(TAG, "peer " + device.addr
							+ " sends connecting to me");

					// check if trying to conn to self
					if (device.addr != null && device.addr.equals(mDevice.addr)) {
						Log.d(TAG, "CONN_TO_SELF: deny self connection");
						Intent in = new Intent(Router.ACTION_DENY_CONNECTION);
						in.putExtra(Router.PEER_NAME, device.name);
						in.putExtra(Router.PEER_ADDR, device.addr);
						in.putExtra(Router.PEER_PORT, device.port);
						in.putExtra(Router.CONN_DENY_CODE, Router.ConnFailureCode.FAIL_CONN_SELF);
						activity.startService(in);
						return;
					}

					// auto accept connection from peer
					Log.d(TAG, "accept peer's connection attempt from: "
							+ device.addr);
					Intent in = new Intent(Router.ACTION_ACCEPT_CONNECTION);
					in.putExtra(Router.PEER_NAME, device.name);
					in.putExtra(Router.PEER_ADDR, device.addr);
					in.putExtra(Router.PEER_PORT, device.port);
					activity.startService(in);
				} else if (Router.ACTION_CONNECTION_FAILED.equals(action)) {
					Log.d(TAG, "connection_failed");
				} else if (Router.ACTION_GET_DEVICE_INFO.equals(action)) {
					pname = intent.getStringExtra(Router.PEER_NAME);
					paddr = intent.getStringExtra(Router.PEER_ADDR);
					pport = intent.getStringExtra(Router.PEER_PORT);
					device = new DeviceInfo(pname,paddr,pport);
					mDevice = device;
					Log.d(TAG, "onGetDeviceInfo: " + device.toString());
					// my device connect to net and got deviceinfo, 
					//start search for peers
					Log.d(TAG, "start peer search");
					Intent in = new Intent(Router.ACTION_START_SEARCH);
					in.putExtra(Router.SEARCH_TIMEOUT, searchTimeout);
					activity.startService(in);
				} else if (Router.ACTION_ERROR.equals(action)) {
					String errInfo = intent.getStringExtra(Router.MSG_DATA);
					Log.d(TAG, "Error msg: " + errInfo);
				} else if (Router.ACTION_GET_NETWORKS.equals(action)) {
					int[] types = intent.getIntArrayExtra(Router.NET_TYPES);
					String[] names = intent.getStringArrayExtra(Router.NET_NAMES);
					String[] passes = intent.getStringArrayExtra(Router.NET_PASSES);
					String[] infos = intent.getStringArrayExtra(Router.NET_INFOS);
					String[] intfNames = intent.getStringArrayExtra(Router.NET_INTF_NAMES);
					String[] addrs = intent.getStringArrayExtra(Router.NET_ADDRS);

					Log.d(TAG, "onGetNetworks: "
							+ (names != null ? names.length : "null"));
					if (names == null || names.length == 0) {
						updateGuiNoNet();
					} else {
						NetInfo net0 = new NetInfo();
						net0.type = types[0];
						net0.name = names[0];
						net0.pass = passes[0];
						if (infos[0] != null) {
							net0.info = infos[0].getBytes();
						} else {
							net0.info = null;
						}
						net0.intfName = intfNames[0];
						net0.addr = addrs[0];
						mNet = net0; // by default activate the first network
						// first search for current active network
						Intent in = new Intent(Router.ACTION_GET_ACTIVE_NETWORK);
						activity.startService(in);
					}
				} else if (Router.ACTION_GET_ACTIVE_NETWORK.equals(action)) {
					int type = intent.getIntExtra(Router.NET_TYPE, 0);
					String name = intent.getStringExtra(Router.NET_NAME);
					String pass = intent.getStringExtra(Router.NET_PASS);
					String info = intent.getStringExtra(Router.NET_INFO);
					String intfName = intent.getStringExtra(Router.NET_INTF_NAME);
					String addr = intent.getStringExtra(Router.NET_ADDR);
					if (name != null) {
						net = new NetInfo();
						net.type = type;
						net.name = name;
						net.pass = pass;
						if (info != null) {
							net.info = info.getBytes();
						} else {
							net.info = null;
						}
						net.intfName = intfName;
						net.addr = addr;
						mNet = net;
						// update GUI
						updateGuiOnNet(net);
						// get my device info at active network
						Intent in = new Intent(Router.ACTION_GET_DEVICE_INFO);
						activity.startService(in);
					} else {// no active network
						if (mNet != null) {
							Intent in = new Intent(Router.ACTION_ACTIVATE_NETWORK);
							in.putExtra(Router.NET_TYPE,mNet.type);
							in.putExtra(Router.NET_NAME, mNet.name);
							in.putExtra(Router.NET_PASS, mNet.pass);
							in.putExtra(Router.NET_INFO, mNet.info);
							in.putExtra(Router.NET_INTF_NAME, mNet.intfName);
							in.putExtra(Router.NET_ADDR, mNet.addr);
							activity.startService(in);
						} else {
							Log.e(TAG, "mNet is null");
						}
					}
				} else if (Router.ACTION_ACTIVATE_NETWORK.equals(action)) {
					net = new NetInfo();
					net.type = intent.getIntExtra(Router.NET_TYPE, 0);
					net.name = intent.getStringExtra(Router.NET_NAME);
					net.pass = intent.getStringExtra(Router.NET_PASS);
					String netinfo = intent.getStringExtra(Router.NET_INFO);
					net.info = (netinfo == null)?null:netinfo.getBytes();
					net.intfName = intent.getStringExtra(Router.NET_INTF_NAME);
					net.addr = intent.getStringExtra(Router.NET_ADDR);
					Log.d(TAG, "onNetworkActivated: " + net.toString());
					mNet = net;
					// update GUI
					updateGuiOnNet(net);
					// get my device info at active network
					Intent in = new Intent(Router.ACTION_GET_DEVICE_INFO);
					activity.startService(in);
				} else if (Router.ACTION_NETWORK_CONNECTED.equals(action)) {
					net = new NetInfo();
					net.type = intent.getIntExtra(Router.NET_TYPE, 0);
					net.name = intent.getStringExtra(Router.NET_NAME);
					net.pass = intent.getStringExtra(Router.NET_PASS);
					String netinfo = intent.getStringExtra(Router.NET_INFO);
					net.info = (netinfo == null)?null:netinfo.getBytes();
					net.intfName = intent.getStringExtra(Router.NET_INTF_NAME);
					net.addr = intent.getStringExtra(Router.NET_ADDR);
					Log.d(TAG, "onNetworkConnected: "/* +net.toString() */);
					// by default activate newly connected network
					mNet = net;
					Intent in = new Intent(Router.ACTION_ACTIVATE_NETWORK);
					in.putExtra(Router.NET_TYPE,mNet.type);
					in.putExtra(Router.NET_NAME, mNet.name);
					in.putExtra(Router.NET_PASS, mNet.pass);
					in.putExtra(Router.NET_INFO, mNet.info);
					in.putExtra(Router.NET_INTF_NAME, mNet.intfName);
					in.putExtra(Router.NET_ADDR, mNet.addr);
					activity.startService(in);
					
				} else if (Router.ACTION_NETWORK_DISCONNECTED.equals(action)) {
					Log.d(TAG, "onNetworkDisconnected");
					mNet = null;
					updateGuiNoNet();
				} else if (Router.ACTION_SET_CONNECTION_INFO.equals(action)) {
					Log.d(TAG, "finish SetConnectionInfo()");
				} else if (Router.ACTION_GET_CONNECTION_INFO.equals(action)) {
					Log.d(TAG, "onGetConnectionInfo()");
				} else {
					Log.d(TAG, "unhandled intent: " + action);
				}
			}
		};
	}
}
