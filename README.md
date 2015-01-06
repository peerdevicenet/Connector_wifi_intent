Connector_wifi_intent
=====================

This sample connector uses Router's ConnectionService intenting api to discover and connect to peer devices. It communicates thru external wifi router or WifiDirect network setup among a group of WifiDirect enabled devices.

It will instantiate a router service directly, running in a separate process. 

1. In AndroidManifest.xml, add the following permission to enable accessing router APIs:

      <uses-permission android:name="com.xconns.peerdevicenet.permission.REMOTE_MESSAGING" />

	Also since we embed a router instance and we want to expose it for other apps to access, we need to add all Router's manifest info into here.

2. Create a Router instance inside this app by one of two ways:

          * download Router project and include it as a library project, as the eclipse project does.
          * if you are using android's new gradle build system, you can import it as 'com.xconns.peerdevicenet:peerdevicenet-router:1.1.6'.

3. This connector has a single activity ConnectorByWifiIntent with a simple GUI:

		* a TextView showing which network is connected
		* a button to start/stop peer search
		* a ListView showing found and connected peer devices
		* a button to shutdown and disconnect router

4. The major components for talking to ConnectionService:
	
		* intenting api: various intent actions (definedin Router.java) to send messages to router ConnectionService, sent thru activity.startService().
		* broadcast receiver: register to receive callbacks messages from router.

5. These components are created and destroyed following normal life cycle conventions:

		* in activity's onCreate() or fragment's onCreateView(), start Router's ConnectionService, set up intent filter and register broadcast receiver.
		* in onDestroy(), unregister broadcast receiver.
		* Please note we donot kill ConnectionService inside onDestroy(). Connectors setup router's connections so that other apps (Chat, Rotate) can communicate, so we keep router alive by startService(). Router must be explicitly killed by first unbinding all clients and call stopService() with intent ACTION_ROUTER_SHUTDOWN.

6. Typical interaction with ConnectionService involves sending a intent message to router by calling startService() and receive router's reply message at broadcast receiver. Typical workflow for network detection, peer search and device connection consist of the following steps:

	6.1. optionally set connection parameters by sending intent ACTION_SET_CONNECTION_INFO, such as the name this device will show to other peers, require SSL connection or not, etc.

	6.2. find all networks this device is connected to, by sending intent ACTION_GET_NETWORKS. In broadcast receiver when handling callback ACTION_GET_NEWORKS message, if no network found, update GUI showing a message about it. If networks attached, go on to next step.

	6.3. detect which network is active in use for PeerDeviceNet communication, by sending intent ACTION_GET_ACTIVE_NETWORK. When handling callback message ACTION_GET_ACTIVE_NETWORK, if active network is found, goto 6.5.; otherwise goto 6.4..

	6.4. activate one of the attached networks, by sending intent ACTION_ACTIVATE_NETWORK. When handling callbakc message ACTION_ACTIVATE_NETWORK, update GUI showing active network info and go to 6.5.

	6.5. retrieve my device info in active network (ip addr, port), by sending intent ACTION_GET_DEVICE_INFO. When handling callback message ACTION_GET_DEVICE_INFO, we can save device info and start search at 6.6.

	6.6. start searching for peer devices, by sending intent ACTION_START_SEARCH. There are 3 possible callbacks for this call:

		* SEARCH_START: search started, we can update GUI about it.

		* SEARCH_FOUND_DEVICE: a new peer device is found, we can perform authentication or connect to this device at step 6.7.

		* SEARCH_COMPLETE: either search time out or terminated by users.

	6.7. connect to found peer device, by sending intent ACTION_CONNECT. There are 2 possible callbacks:

		* CONNECTED: add the connected device to list view
		
		* CONNECTION_FAILED: the message will contain an error code showing why connection failed (such as rejected by peer). We can show it in GUI or log it.


