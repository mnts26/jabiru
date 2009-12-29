package net.mzet.jabiru.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import net.mzet.jabiru.roster.RosterItem;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;

public class JabberConnection {
	private ConnectionConfiguration xmppConfig;
	private XMPPConnection xmppConnection;
	
	private Roster roster;
	
	private IServiceCallback serviceCallback;
	private ConcurrentHashMap<String,ConcurrentHashMap<String,RosterItem>> groups;
	
	public JabberConnection() {
	}
	
	public boolean connect(String domain, String username, String password) throws XMPPException {
		this.groups = new ConcurrentHashMap<String,ConcurrentHashMap<String,RosterItem>>();
		this.xmppConfig = new ConnectionConfiguration(domain, 5222);
		this.xmppConfig.setReconnectionAllowed(true);
		this.xmppConnection = new XMPPConnection(xmppConfig);
		
		try {
			xmppConnection.connect();
			xmppConnection.login(username, password, "Jabiru");
		}
		catch(Exception e) {
			return false;
		}
		roster = xmppConnection.getRoster();
		createRosterListener();
		return true;
	}

	public void disconnect() {
		xmppConnection.disconnect();
	}
	
	public ConcurrentHashMap<String, RosterItem> rosterGetGroup(String group) {
		if(!groups.containsKey(group)) {
			groups.put(group, new ConcurrentHashMap<String, RosterItem>());
		}
		return groups.get(group);
	}
	
	public void rosterAdd(String jabberid) {
		RosterEntry re = roster.getEntry(jabberid);
		RosterItem ri = new RosterItem(jabberid);
		if(re.getName() != null) {
			ri.setNick(re.getName());
		}
		if(re.getGroups().isEmpty()) {
			rosterGetGroup("").put(jabberid, ri);
		}
		else {
			for(RosterGroup rg : re.getGroups()) {
				rosterGetGroup(rg.getName()).put(jabberid, ri);
			}
		}
	}
	
	public void registerServiceCallback(IServiceCallback serviceCallback) {
		this.serviceCallback = serviceCallback;
	}
	
	public void createRosterListener() {
		roster.addRosterListener(new RosterListener() {

			@Override
			public void entriesAdded(Collection<String> entries) {
				for(String entry : entries) {
					rosterAdd(entry);
				}
				if(serviceCallback != null) {
					serviceCallback.rosterChanged();
				}
			}

			@Override
			public void entriesDeleted(Collection<String> entries) {
			}

			@Override
			public void entriesUpdated(Collection<String> entries) {
			}

			@Override
			public void presenceChanged(Presence presence) {
				//presence.
			}
			
		});
	}
	
	public ArrayList<String> getRosterGroups() {
		ArrayList<String> rg = new ArrayList<String>(groups.keySet());
		Collections.sort(rg);
		
		return rg;
	}
	
	public ArrayList<RosterItem> getRosterItems(String group) {
		System.out.println(group);
		ArrayList<RosterItem> ri = new ArrayList<RosterItem>(groups.get(group).values());
		Collections.sort(ri);
		
		return ri;
	}

	public boolean isLogged() {
		return xmppConnection != null && xmppConnection.isConnected() && xmppConnection.isAuthenticated(); 
	}
}
