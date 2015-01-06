package com.xconns.peerdevicenet.samples.connector_wifi_intent;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;

public class ConnectorByWifiIntent extends Activity {
	private static final String TAG = "ConnectorWifiIntent";

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

	private HashSet<String> connPeers = new HashSet<String>();
	private DeviceInfo mDevice = null; // my own device info
	private NetInfo mNet = null; // network my device connect to
	// peer connection parameters
	private String securityToken = ""; // dont check conn security token
	private int connTimeout = 5000; // 5 seconds for socket conn timeout
	private int searchTimeout = 30000; // 30 seconds timeout for searching //
										// peers

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connector_by_wifi_intent);

		setupNetText = getResources().getText(R.string.setup_net);
		stopSearchText = getResources().getText(R.string.stop_search);
		searchConnectText = getResources().getText(R.string.search_connect);
		onNetText = getResources().getText(R.string.on_net);
		missNetText = getResources().getText(R.string.miss_net);

		mNetMsg = (TextView) findViewById(R.id.net_msg);
		mConnButton = (Button) findViewById(R.id.button_conn);
		mConnButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mNet == null) {
					configWifi();
				} else {
					Log.d(TAG, "start peer search");
					Intent in = new Intent(Router.Intent.ACTION_START_SEARCH);
					in.putExtra(Router.MsgKey.SEARCH_TIMEOUT, searchTimeout);
					startService(in);
				}
			}
		});
		mDoneButton = (Button) findViewById(R.id.button_done);
		mDoneButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// shutdon router by calling stopService
				Intent intent = new Intent(Router.Intent.ACTION_ROUTER_SHUTDOWN);
				stopService(intent);
				//
				finish();
			}
		});

		// Initialize the array adapter for the conversation thread
		mPeerListAdapter = new ArrayAdapter<String>(this,
				R.layout.peer_name);
		mPeerListView = (ListView) findViewById(R.id.peers_list);
		mPeerListView.setAdapter(mPeerListAdapter);

		// start router service by intent
		// so it can keep running at background
		// even when client unbind
		// must be stopped by calling stopService after all clients unbind
		Intent intent = new Intent(Router.Intent.ACTION_CONNECTION_SERVICE);
		startService(intent);

		// register recvers to handle ConnectionService related broadcast
		// intents
		IntentFilter filter = new IntentFilter();
		filter.addAction(Router.Intent.ACTION_GET_NETWORKS);
		filter.addAction(Router.Intent.ACTION_GET_ACTIVE_NETWORK);
		filter.addAction(Router.Intent.ACTION_ACTIVATE_NETWORK);
		filter.addAction(Router.Intent.ACTION_NETWORK_CONNECTED);
		filter.addAction(Router.Intent.ACTION_NETWORK_DISCONNECTED);
		filter.addAction(Router.Intent.ACTION_SEARCH_START);
		filter.addAction(Router.Intent.ACTION_SEARCH_FOUND_DEVICE);
		filter.addAction(Router.Intent.ACTION_SEARCH_COMPLETE);
		filter.addAction(Router.Intent.ACTION_CONNECTING);
		filter.addAction(Router.Intent.ACTION_CONNECTION_FAILED);
		filter.addAction(Router.Intent.ACTION_CONNECTED);
		filter.addAction(Router.Intent.ACTION_DISCONNECTED);
		filter.addAction(Router.Intent.ACTION_SET_CONNECTION_INFO);
		filter.addAction(Router.Intent.ACTION_GET_CONNECTION_INFO);
		filter.addAction(Router.Intent.ACTION_GET_DEVICE_INFO);
		registerReceiver(mReceiver, filter);

		// setup my device name known to peers
		String myDeviceName = android.os.Build.MODEL;
		if (myDeviceName == null || myDeviceName.length() == 0) {
			myDeviceName = "MyDeviceName";
		}
		Intent in = new Intent(Router.Intent.ACTION_SET_CONNECTION_INFO);
		in.putExtra(Router.MsgKey.DEVICE_NAME, myDeviceName);
		in.putExtra(Router.MsgKey.USE_SSL, false);
		startService(in);

		// start by checking if device is connected to any networks
		in = new Intent(Router.Intent.ACTION_GET_NETWORKS);
		startService(in);

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
		unregisterReceiver(mReceiver);
		// don't stop RouterService here
	}

	private void configWifi() {
		Intent in = new Intent(Settings.ACTION_WIFI_SETTINGS);
		startActivity(in);
	}

	private void updateGuiNoNet() {
		mNetMsg.setText(missNetText);
		mConnButton.setText(setupNetText);
	}

	private void updateGuiOnNet(NetInfo net) {
		mNetMsg.setText(onNetText + ": " + net.name
				+ ", start searching and connecting to peer devices");
		mConnButton.setText(searchConnectText);
	}

	private void updateGuiSearchStart() {
		mConnButton.setText(stopSearchText);
	}

	private void updateGuiSearchComplete() {
		mConnButton.setText(searchConnectText);
	}

	private void addDeviceToList(DeviceInfo dev) {
		mPeerListAdapter.add(dev.name + " : " + dev.addr);
		mPeerListAdapter.notifyDataSetChanged();
	}

	private void delDeviceFromList(DeviceInfo dev) {
		mPeerListAdapter.remove(dev.name + " : " + dev.addr);
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
			if (Router.Intent.ACTION_SEARCH_FOUND_DEVICE.equals(action)) {
				// handle msgs for scan
				pname = intent.getStringExtra(Router.MsgKey.PEER_NAME);
				paddr = intent.getStringExtra(Router.MsgKey.PEER_ADDR);
				pport = intent.getStringExtra(Router.MsgKey.PEER_PORT);
				boolean uSSL = intent.getBooleanExtra(Router.MsgKey.USE_SSL, false);
				device = new DeviceInfo(pname, paddr, pport);
				Log.d(TAG, "onSearchFoundDevice: " + device);

				// after find devices
				// auto connect to them
				// connect from device with small ip to device with large ip
				if (!connPeers.contains(device.addr)) {
					if (mDevice.addr.compareTo(device.addr) < 0) {
						Log.d(TAG, "connect to client: " + device.addr);
						Intent in = new Intent(Router.Intent.ACTION_CONNECT);
						in.putExtra(Router.MsgKey.PEER_NAME, device.name);
						in.putExtra(Router.MsgKey.PEER_ADDR, device.addr);
						in.putExtra(Router.MsgKey.PEER_PORT, device.port);
						in.putExtra(Router.MsgKey.AUTHENTICATION_TOKEN,
								securityToken.getBytes());
						in.putExtra(Router.MsgKey.CONNECT_TIMEOUT, connTimeout);
						startService(in);
						connPeers.add(device.addr);
					}
				}

			} else if (Router.Intent.ACTION_SEARCH_COMPLETE.equals(action)) {
				Log.d(TAG, "search complete");
				updateGuiSearchComplete();
			} else if (Router.Intent.ACTION_SEARCH_START.equals(action)) {
				updateGuiSearchStart();
			} else if (Router.Intent.ACTION_CONNECTED.equals(action)) {
				pname = intent.getStringExtra(Router.MsgKey.PEER_NAME);
				paddr = intent.getStringExtra(Router.MsgKey.PEER_ADDR);
				pport = intent.getStringExtra(Router.MsgKey.PEER_PORT);
				device = new DeviceInfo(pname, paddr, pport);
				addDeviceToList(device);
				Log.d(TAG, "a device connected");
			} else if (Router.Intent.ACTION_DISCONNECTED.equals(action)) {
				pname = intent.getStringExtra(Router.MsgKey.PEER_NAME);
				paddr = intent.getStringExtra(Router.MsgKey.PEER_ADDR);
				pport = intent.getStringExtra(Router.MsgKey.PEER_PORT);
				connPeers.remove(paddr);
				device = new DeviceInfo(pname, paddr, pport);
				delDeviceFromList(device);
				Log.d(TAG, "a device disconnected: " + device.addr);
			} else if (Router.Intent.ACTION_GET_CONNECTED_PEERS.equals(action)) {
				String[] names = intent.getStringArrayExtra(Router.MsgKey.PEER_NAMES);
				String[] addrs = intent.getStringArrayExtra(Router.MsgKey.PEER_ADDRS);
				String[] ports = intent.getStringArrayExtra(Router.MsgKey.PEER_PORTS);
				for (int i = 0; i < names.length; i++) {
					addDeviceToList(new DeviceInfo(names[i], addrs[i], ports[1]));
				}
				Log.d(TAG, "get_connected_peers");
			} else if (Router.Intent.ACTION_CONNECTING.equals(action)) {
				pname = intent.getStringExtra(Router.MsgKey.PEER_NAME);
				paddr = intent.getStringExtra(Router.MsgKey.PEER_ADDR);
				pport = intent.getStringExtra(Router.MsgKey.PEER_PORT);
				device = new DeviceInfo(pname, paddr, pport);
				byte[] token = intent
						.getByteArrayExtra(Router.MsgKey.AUTHENTICATION_TOKEN);
				Log.d(TAG, "peer " + device.addr + " sends connecting to me");

				// check if trying to conn to self
				if (device.addr != null && device.addr.equals(mDevice.addr)) {
					Log.d(TAG, "CONN_TO_SELF: deny self connection");
					Intent in = new Intent(Router.Intent.ACTION_DENY_CONNECTION);
					in.putExtra(Router.MsgKey.PEER_NAME, device.name);
					in.putExtra(Router.MsgKey.PEER_ADDR, device.addr);
					in.putExtra(Router.MsgKey.PEER_PORT, device.port);
					in.putExtra(Router.MsgKey.CONN_DENY_CODE,
							Router.ConnFailureCode.FAIL_CONN_SELF);
					startService(in);
					return;
				}

				// auto accept connection from peer
				Log.d(TAG, "accept peer's connection attempt from: "
						+ device.addr);
				Intent in = new Intent(Router.Intent.ACTION_ACCEPT_CONNECTION);
				in.putExtra(Router.MsgKey.PEER_NAME, device.name);
				in.putExtra(Router.MsgKey.PEER_ADDR, device.addr);
				in.putExtra(Router.MsgKey.PEER_PORT, device.port);
				startService(in);
			} else if (Router.Intent.ACTION_CONNECTION_FAILED.equals(action)) {
				pname = intent.getStringExtra(Router.MsgKey.PEER_NAME);
				paddr = intent.getStringExtra(Router.MsgKey.PEER_ADDR);
				pport = intent.getStringExtra(Router.MsgKey.PEER_PORT);
				connPeers.remove(paddr);
				Log.d(TAG, "connection_failed");
			} else if (Router.Intent.ACTION_GET_DEVICE_INFO.equals(action)) {
				pname = intent.getStringExtra(Router.MsgKey.PEER_NAME);
				paddr = intent.getStringExtra(Router.MsgKey.PEER_ADDR);
				pport = intent.getStringExtra(Router.MsgKey.PEER_PORT);
				device = new DeviceInfo(pname, paddr, pport);
				mDevice = device;
				Log.d(TAG, "onGetDeviceInfo: " + device.toString());
				// my device connect to net and got deviceinfo,
				// start search for peers
				Log.d(TAG, "start peer search");
				Intent in = new Intent(Router.Intent.ACTION_START_SEARCH);
				in.putExtra(Router.MsgKey.SEARCH_TIMEOUT, searchTimeout);
				startService(in);
			} else if (Router.Intent.ACTION_ERROR.equals(action)) {
				String errInfo = intent.getStringExtra(Router.MsgKey.MSG_DATA);
				Log.d(TAG, "Error msg: " + errInfo);
			} else if (Router.Intent.ACTION_GET_NETWORKS.equals(action)) {
				int[] types = intent.getIntArrayExtra(Router.MsgKey.NET_TYPES);
				String[] names = intent.getStringArrayExtra(Router.MsgKey.NET_NAMES);
				String[] passes = intent.getStringArrayExtra(Router.MsgKey.NET_PASSES);
				String[] infos = intent.getStringArrayExtra(Router.MsgKey.NET_INFOS);
				String[] intfNames = intent
						.getStringArrayExtra(Router.MsgKey.NET_INTF_NAMES);
				String[] addrs = intent.getStringArrayExtra(Router.MsgKey.NET_ADDRS);

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
					Intent in = new Intent(Router.Intent.ACTION_GET_ACTIVE_NETWORK);
					startService(in);
				}
			} else if (Router.Intent.ACTION_GET_ACTIVE_NETWORK.equals(action)) {
				int type = intent.getIntExtra(Router.MsgKey.NET_TYPE, 0);
				String name = intent.getStringExtra(Router.MsgKey.NET_NAME);
				String pass = intent.getStringExtra(Router.MsgKey.NET_PASS);
				String info = intent.getStringExtra(Router.MsgKey.NET_INFO);
				String intfName = intent.getStringExtra(Router.MsgKey.NET_INTF_NAME);
				String addr = intent.getStringExtra(Router.MsgKey.NET_ADDR);
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
					Intent in = new Intent(Router.Intent.ACTION_GET_DEVICE_INFO);
					startService(in);
				} else {// no active network
					if (mNet != null) {
						Intent in = new Intent(Router.Intent.ACTION_ACTIVATE_NETWORK);
						in.putExtra(Router.MsgKey.NET_TYPE, mNet.type);
						in.putExtra(Router.MsgKey.NET_NAME, mNet.name);
						in.putExtra(Router.MsgKey.NET_PASS, mNet.pass);
						in.putExtra(Router.MsgKey.NET_INFO, mNet.info);
						in.putExtra(Router.MsgKey.NET_INTF_NAME, mNet.intfName);
						in.putExtra(Router.MsgKey.NET_ADDR, mNet.addr);
						startService(in);
					} else {
						Log.e(TAG, "mNet is null");
					}
				}
			} else if (Router.Intent.ACTION_ACTIVATE_NETWORK.equals(action)) {
				net = new NetInfo();
				net.type = intent.getIntExtra(Router.MsgKey.NET_TYPE, 0);
				net.name = intent.getStringExtra(Router.MsgKey.NET_NAME);
				net.pass = intent.getStringExtra(Router.MsgKey.NET_PASS);
				String netinfo = intent.getStringExtra(Router.MsgKey.NET_INFO);
				net.info = (netinfo == null) ? null : netinfo.getBytes();
				net.intfName = intent.getStringExtra(Router.MsgKey.NET_INTF_NAME);
				net.addr = intent.getStringExtra(Router.MsgKey.NET_ADDR);
				Log.d(TAG, "onNetworkActivated: " + net.toString());
				mNet = net;
				// update GUI
				updateGuiOnNet(net);
				// a new network activate, clear foundDevices
				connPeers.clear();
				// get my device info at active network
				Intent in = new Intent(Router.Intent.ACTION_GET_DEVICE_INFO);
				startService(in);
			} else if (Router.Intent.ACTION_NETWORK_CONNECTED.equals(action)) {
				net = new NetInfo();
				net.type = intent.getIntExtra(Router.MsgKey.NET_TYPE, 0);
				net.name = intent.getStringExtra(Router.MsgKey.NET_NAME);
				net.pass = intent.getStringExtra(Router.MsgKey.NET_PASS);
				String netinfo = intent.getStringExtra(Router.MsgKey.NET_INFO);
				net.info = (netinfo == null) ? null : netinfo.getBytes();
				net.intfName = intent.getStringExtra(Router.MsgKey.NET_INTF_NAME);
				net.addr = intent.getStringExtra(Router.MsgKey.NET_ADDR);
				Log.d(TAG, "onNetworkConnected: "/* +net.toString() */);
				// by default activate newly connected network
				mNet = net;
				Intent in = new Intent(Router.Intent.ACTION_ACTIVATE_NETWORK);
				in.putExtra(Router.MsgKey.NET_TYPE, mNet.type);
				in.putExtra(Router.MsgKey.NET_NAME, mNet.name);
				in.putExtra(Router.MsgKey.NET_PASS, mNet.pass);
				in.putExtra(Router.MsgKey.NET_INFO, mNet.info);
				in.putExtra(Router.MsgKey.NET_INTF_NAME, mNet.intfName);
				in.putExtra(Router.MsgKey.NET_ADDR, mNet.addr);
				startService(in);

			} else if (Router.Intent.ACTION_NETWORK_DISCONNECTED.equals(action)) {
				Log.d(TAG, "onNetworkDisconnected");
				mNet = null;
				updateGuiNoNet();
			} else if (Router.Intent.ACTION_SET_CONNECTION_INFO.equals(action)) {
				Log.d(TAG, "finish SetConnectionInfo()");
			} else if (Router.Intent.ACTION_GET_CONNECTION_INFO.equals(action)) {
				Log.d(TAG, "onGetConnectionInfo()");
			} else {
				Log.d(TAG, "unhandled intent: " + action);
			}
		}
	};
}
