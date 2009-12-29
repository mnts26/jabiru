package net.mzet.jabiru;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.mzet.jabiru.roster.IRosterCallback;
import net.mzet.jabiru.roster.RosterAdapter;
import net.mzet.jabiru.roster.RosterItem;
import net.mzet.jabiru.roster.RosterListAdapter;
import net.mzet.jabiru.service.IRosterConnection;
import net.mzet.jabiru.service.JabberService;
import net.mzet.jabiru.settings.AccountSettings;

import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Toast;

public class Main extends ExpandableListActivity {
	public static final int MENU_CONNECT    = Menu.FIRST;
	public static final int MENU_ACCOUNT    = Menu.FIRST + 1;
	public static final int MENU_OFFLINE    = Menu.FIRST + 2;
	public static final int MENU_GROUPCHAT  = Menu.FIRST + 3;
	public static final int MENU_OPTIONS    = Menu.FIRST + 4;
	public static final int MENU_STATUS     = Menu.FIRST + 5;
	
	ProgressDialog progressDialog;
	
	private ServiceConnection serviceConnection;
	private Intent serviceIntent;
	private RosterAdapter serviceAdapter;
	
	private IRosterCallback.Stub callback;
	private Handler mainHandler = new Handler();
	private Menu menu;
	
	private SimpleExpandableListAdapter listAdapter;
	private List<ArrayList<HashMap<String,String>>> listChild = new ArrayList<ArrayList<HashMap<String,String>>>();
	private List<HashMap<String,String>> listGroup = new ArrayList<HashMap<String,String>>();
	int ix = 0;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerJabberService();
        createCallback();
        setContentView(R.layout.main);
        String[] groupIDs = {"group"};
        String[] childIDs = {"nick"};
        listAdapter = new RosterListAdapter(this, listGroup, R.layout.roster_group, groupIDs, new int[] { R.id.group_name }, listChild, R.layout.roster_child, childIDs, new int[] { R.id.child_name });
        
        setListAdapter(listAdapter);
    }
	
	@Override
	protected void onPause() {
		super.onPause();
		if(serviceAdapter != null) {
			serviceAdapter.unregisterCallback(callback);
		}
		unbindJabberService();
	}

	@Override
	protected void onResume() {
		super.onResume();
		bindJabberService();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {		
		super.onCreateOptionsMenu(menu);
		
		this.menu = menu;
		
		menu.add(0, MENU_CONNECT, 0, serviceAdapter != null && serviceAdapter.isLogged() ? R.string.menu_disconnect : R.string.menu_connect); 
		menu.add(0, MENU_OFFLINE, 0, R.string.menu_offline_on);
		menu.add(0, MENU_STATUS, 0, R.string.menu_status); 		
		menu.add(0, MENU_ACCOUNT, 0, R.string.menu_account); 
		menu.add(0, MENU_GROUPCHAT, 0, R.string.menu_groupchat); 
		menu.add(0, MENU_OPTIONS, 0, R.string.menu_options); 
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case MENU_CONNECT:
			toggleConnection(item);
			return true;
		case MENU_ACCOUNT:
			startActivity(new Intent(this, AccountSettings.class));
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	private void toggleConnection(MenuItem item) {
		if(serviceAdapter != null && serviceAdapter.isLogged()) {
			(new Thread() {
				public void run() {
					serviceAdapter.disconnect();
					stopService(serviceIntent);
				}
			}).start();
		}
		else {
			progressDialog = ProgressDialog.show(this, "Jabber Connection", "Connecting...");
			(new Thread() {
				public void run() {
					serviceAdapter.connect();
				}
			}).start();
		}
	}

	public void updateRoster() {
		listGroup.clear();
		listChild.clear();
		
		ArrayList<String> rosterGroups = serviceAdapter.getRosterGroups();
		for(String group : rosterGroups) {
			ArrayList<RosterItem> rosterItems = serviceAdapter.getRosterItems(group);
			
			group = group.length() == 0 ? "General" : group;
			HashMap<String,String> tmpHM1 = new HashMap<String,String>();
			tmpHM1.put("group", group);
			listGroup.add(tmpHM1);
			
			ArrayList<HashMap<String,String>> tmpAL2 = new ArrayList<HashMap<String,String>>();
			for(RosterItem item : rosterItems) {
				HashMap<String,String> tmpHM2 = new HashMap<String,String>();
				tmpHM2.put("nick", item.getNick());
				tmpAL2.add(tmpHM2);
			}
			listChild.add(tmpAL2);
		}
		listAdapter.notifyDataSetChanged();
	}
	
	public void clearRoster() {
		listGroup.clear();
		listChild.clear();
		listAdapter.notifyDataSetChanged();
	}
	
	private void showToastNotification(int message) {
		Toast tmptoast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		tmptoast.show();
	}

	private void registerJabberService() {
		serviceIntent = new Intent(this, JabberService.class);
		serviceIntent.setAction("net.mzet.jabiru.JABBERSERVICE");
		serviceConnection = new ServiceConnection() {

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				serviceAdapter = new RosterAdapter(IRosterConnection.Stub.asInterface(service));
				serviceAdapter.registerCallback(callback);
				if(serviceAdapter.isLogged()) {
					updateRoster();
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
			}
		};
	}
	
	private void bindJabberService() {
		bindService(serviceIntent, serviceConnection, 0);
		startService(serviceIntent);
	}
	
	private void unbindJabberService() {
		System.out.println("unbind");
		if(!serviceAdapter.isLogged()) {
			stopService(serviceIntent);
		}
		unbindService(serviceConnection);
	}
	
	private void createCallback() {
		callback = new IRosterCallback.Stub() {
			
			@Override
			public void rosterChanged() throws RemoteException {
				mainHandler.post(new Runnable() {
					@Override
					public void run() {
						updateRoster();
					}
				});
			}
			
			@Override
			public void connectOk() throws RemoteException {
				mainHandler.post(new Runnable() {
					@Override
					public void run() {
						showToastNotification(R.string.toast_connect_ok);
						if(progressDialog != null && progressDialog.isShowing()) {
							progressDialog.dismiss();
						}
						if(menu != null) {
							menu.findItem(MENU_CONNECT).setTitle(R.string.menu_disconnect);
						}
					}
				});
			}
			
			@Override
			public void connectFail() throws RemoteException {
				mainHandler.post(new Runnable() {
					@Override
					public void run() {
						if(progressDialog != null && progressDialog.isShowing()) {
							progressDialog.dismiss();
						}
						showToastNotification(R.string.toast_connect_fail);
					}
				});
			}

			@Override
			public void disconnect() throws RemoteException {
				mainHandler.post(new Runnable() {
					@Override
					public void run() {
						if(menu != null) {
							menu.findItem(MENU_CONNECT).setTitle(R.string.menu_connect);
							clearRoster();
						}
					}
				});
			}
		};
	}
}
