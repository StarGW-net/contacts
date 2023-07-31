package net.stargw.contacts;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

public class Contacts extends Activity {

	private Context myContext;

	/*
	private static final String name = "My Contacts";
	private static final String type = "eu.stargw";
	*/
	String name;
	String type;

	static final String TAG = "ContactsLog";

	private ArrayList accRecords;

	// Request code for READ_CONTACTS. It can be any number > 0.
	private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;

		// My own class for storing data.
	// Submit cursor queries and then build my data from them
	public static class Item {
		private String mText;
		private long mId;

		public Item(String text, long id) {
			mText = text;
			mId = id;
		}

		public long getId() {
			return mId;
		}

		public void setId(long id) {
			mId = id;
		}

		public String getmText() {
			return mText;
		}

		public void setmText(String mText) {
			this.mText = mText;
		}

		@Override
		public String toString() {
			return mText;
		}
	}

	public static class AccRecord {
		private String name;
		private String type;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
										   int[] grantResults) {
		if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Permission is granted
				ListGroups();
			} else {
				Toast.makeText(this, "Until you grant the permission, contacts cannot be displayed", Toast.LENGTH_SHORT).show();
			}
		}
	}


	// Global variables :-)
	private ListView lv;
	private List<Item> groups = new ArrayList<Contacts.Item>();
	private List<Item> people = new ArrayList<Contacts.Item>();
	int state = 0; // List ALL Groups
	
	//
	// This handles the BACK key process
	//
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			switch(state) {

			case 1: // Currently listing people in a group
			case 2: // Currently listing  ALL people
				ListGroups();
				return false;

			case 0: // Currently Listing ALL Groups
				return super.onKeyDown(keyCode, event);
			}
		}
		return false;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		myContext = this;

		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(myContext);
		name = p.getString("account_name", "My Contacts");
		type = p.getString("account_type", "eu.stargw");

		setContentView(R.layout.main);

		TextView btn = (TextView) findViewById(R.id.activity_main_title);
		btn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showOptionsMenu(v);
			}
		});

		ImageView btn2 = (ImageView) findViewById(R.id.icon);
		btn2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showHelp();
			}
		});

		/*
		btn2 = (ImageView) findViewById(R.id.arrow);
		btn2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showOptionsMenu(v);
			}
		});
		*/






		// Get the ListView
		lv = (ListView) findViewById(R.id.contactList);
		
		lv.setOnItemClickListener(new OnItemClickListener() 
		{ 
			public void onItemClick(AdapterView<?> parent, View view, int position , long id) { 
				Selection(position);
			}
		});
		


		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
			//After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
		} else {
			// Start with a Group view of ALL groups
			ListGroups();
		}


	}


	//
	// Handle when an item is selected from the ListView
	//
	private void Selection(int position)
	{
		long n = 0;
		
		switch(state) {

		case 0: // Displaying groups
			n = groups.get(position).getId();

			// Switch to displaying people
			if (n == 0) // Special case - all people
			{
				ListALLPeople();
			} else {
				ListPeople(n);
			}

			break;

		case 1: // Displaying people
		case 2: // Currently listing  ALL people
			n = people.get(position).getId();
			Log.w(TAG, "opening contact with ID: " + n);
			Intent intent = new Intent(Intent.ACTION_VIEW);
			Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, Long.toString(n));
			intent.setData(uri);
			startActivity(intent);
			break;
		}
	}


	//
	// Get ALL people - not this queries contacts directly
	//
	private void ListALLPeople()
	{
		state = 2;
		people.clear();
		
		// Note the different URI to GetPeople function
		Uri uri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, type)
				.build();

		String[] projection = new String[] {
				ContactsContract.Contacts._ID,
				ContactsContract.Contacts.DISPLAY_NAME,
		};

		String selection = null;
		
		String[] selectionArgs = null;
		
		String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
		
		ContentResolver cr = getContentResolver();
		Cursor cur = cr.query(uri, projection, selection, selectionArgs, sortOrder);
		
		if (cur.getCount() > 0) 
		{
			while (cur.moveToNext()) 
			{
				String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
				String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));	

				people.add(new Item(
						name,
						Long.parseLong(id)));
			}
		}
		cur.close();

		ArrayAdapter adapter1 = new ArrayAdapter(this, R.layout.row, R.id.contactEntryText, people);

		addSearch(adapter1);

		lv.setAdapter(adapter1);
		lv.setFastScrollEnabled(true);
	}

	
	//
	// Get people in a group - this gets the raw ID which is the Contact ID to use
	//
	private void ListPeople(Long group)
	{
		state = 1;
		people.clear();
		
		// Note the different URI to GetALLPeople function
		// Uri uri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
		Uri uri = ContactsContract.Data.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, type)
				.build();

		String selection = ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + "="
			+ Long.toString(group) + " AND "
			+ ContactsContract.CommonDataKinds.GroupMembership.MIMETYPE + "='"
			+ ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE + "'";

		String[] projection = new String[] {
				// ContactsContract.Data.RAW_CONTACT_ID,  // we do not want raw here because we pass to contacts app
				ContactsContract.Data.CONTACT_ID, // Aggregate _ID
				ContactsContract.Data.DISPLAY_NAME,
		};

		String[] selectionArgs = null;
		
		String sortOrder = ContactsContract.Data.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

		ContentResolver cr = getContentResolver();
		Cursor cur = cr.query(uri, projection, selection, selectionArgs, sortOrder);
		
		if (cur.getCount() > 0) {
			while (cur.moveToNext()) {

				// Raw Contact ID is the one we want here
				// String id = cur.getString(cur.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)); 
				String id = cur.getString(cur.getColumnIndex(ContactsContract.Data.CONTACT_ID)); // Aggregate _ID
				String name = cur.getString(cur.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));	

				
				people.add(new Item(
						name,
						Long.parseLong(id)));

			}
		}
		cur.close();
		
		ArrayAdapter adapter1 = new ArrayAdapter(this, R.layout.row, R.id.contactEntryText, people);

		addSearch(adapter1);

		lv.setAdapter(adapter1);
		lv.setFastScrollEnabled(false);
	}

	//
	// Get a list of all the groups
	//
	private void ListGroups()
	{
		state = 0;


		groups.clear();
		groups.add(new Item("* ALL Contacts",0));

		TextView text = (TextView) findViewById(R.id.activity_main_title);
		// text.setText("⏷ " + name); // Triangle does not display properly
		text.setText(name);

		// Cursor cur = cr.query(ContactsContract.Groups.CONTENT_URI, null, null, null, null);
		
		// Uri uri = ContactsContract.Groups.CONTENT_URI; 
		Uri uri = ContactsContract.Groups.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, type)
				.build();
		
		String[] projection = new String[] {
				ContactsContract.Groups._ID,
				ContactsContract.Groups.TITLE
		};

		String selection = null;

		// This causes a crash. Why???
		// String selection = ContactsContract.Groups.ACCOUNT_NAME + "=pcsc";

		String[] selectionArgs = null;
		String sortOrder = ContactsContract.Groups.TITLE + " COLLATE LOCALIZED ASC";

		ContentResolver cr = getContentResolver();
		Cursor cur = cr.query(uri, projection, selection, selectionArgs, sortOrder);
		
		if (cur.getCount() > 0) {
			while (cur.moveToNext()) {

				// Raw Contact ID is the one we want here
				String id = cur.getString(cur.getColumnIndex(ContactsContract.Groups._ID));
				String name = cur.getString(cur.getColumnIndex(ContactsContract.Groups.TITLE));	

				
				groups.add(new Item(
						name,
						Long.parseLong(id)));

			}
			cur.close();

			ArrayAdapter adapter1 = new ArrayAdapter(this, R.layout.row, R.id.contactEntryText, groups);

			addSearch(adapter1);

			lv.setAdapter(adapter1);
			lv.setFastScrollEnabled(false);
		} else {
			cur.close();
			ListALLPeople();
		}

		
	}



	public void showOptionsMenu(View v) {
		PopupMenu popup = new PopupMenu(this, v);
		popup.inflate(R.menu.menu);
		// getMenuInflater().inflate(R.menu.menu_main, menu);

		final Menu m = popup.getMenu();

		m.clear();

		AccountManager am = AccountManager.get(getApplicationContext());

		final Account[] accounts = am.getAccounts();

		AuthenticatorDescription[] accountTypes = AccountManager.get(myContext).getAuthenticatorTypes();

		accRecords = new ArrayList<AccRecord>();

		int n = 0;
		for (Account acc : accounts)
		{
			// accountName[n] = acc.name;
			// accountType[n] = acc.type;

			// Get friendly display name
			AuthenticatorDescription ad = getAuthenticatorDescription(acc.type, accountTypes);

			String d;
			try {
				PackageManager pm = getPackageManager();
				String s = pm.getText(ad.packageName, ad.labelId, null).toString();
				d = acc.name + " (" + s + ")";
			} catch (Exception e) {
				d = acc.name + " (" + acc.type + ")";
			}
			m.add(0, n, n, d);
			n++;
			Log.w(TAG, "Got Account: " + d);
		}

		invalidateOptionsMenu();


		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				// if (item.getItemId()) {
				int p = item.getItemId();
				Log.w(TAG, "Picked Item: " + p);
				Log.w(TAG, "Picked Account: " + accounts[p].name);
				name = accounts[p].name;
				type =  accounts[p].type;
				SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(myContext);
				pm.edit().putString("account_name", name).apply();
				pm.edit().putString("account_type", type).apply();
				ListGroups();
				return true;
			}

		});
        popup.show();
	}

	private static AuthenticatorDescription getAuthenticatorDescription(String type, AuthenticatorDescription[] dictionary) {
		for (int i = 0; i < dictionary.length; i++) {
			if (dictionary[i].type.equals(type)) {
				return dictionary[i];
			}
		}
		// No match found
		return null;
	}

	private void addSearch(ArrayAdapter adapter)
	{

		final ArrayAdapter a1 = adapter;

		ImageView mySearch = (ImageView) findViewById(R.id.activity_main_filter_icon);

		EditText myFilter = (EditText) findViewById(R.id.activity_main_filter_text);
		myFilter.setText("");
		myFilter.setVisibility(View.GONE);

		mySearch.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				//v.getId() will give you the image id

				EditText myFilter = (EditText) findViewById(R.id.activity_main_filter_text);

				if (myFilter.getVisibility() == View.GONE) {

					myFilter.setVisibility(View.VISIBLE);
					myFilter.setFocusableInTouchMode(true);
					myFilter.requestFocus();

					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

					myFilter.addTextChangedListener(new TextWatcher() {

						public void afterTextChanged(Editable s) {
						}

						public void beforeTextChanged(CharSequence s, int start, int count, int after) {
						}

						public void onTextChanged(CharSequence s, int start, int before, int count) {

							// Global.myLog("Filter on text: " + s , 3);
							a1.getFilter().filter(s.toString());
						}
					});
				} else {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(myFilter.getWindowToken(), 0);

					myFilter.setVisibility(View.GONE);
					myFilter.setText("");
					// adapter.getFilter().filter(null);
				}

			}
		});
	}

	//
	// Display the help screen
	//
	private void showHelp()
	{

		String verName = "latest";
		try {

			PackageInfo pInfo = myContext.getPackageManager().getPackageInfo(getPackageName(), 0);
			verName = pInfo.versionName;

		} catch (PackageManager.NameNotFoundException e) {
			verName = "unknown";
		}

		String app = getString(R.string.app_name);

		String url = "https://www.stargw.net/android/help.html?ver=" + verName + "&app=" + app;

		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		startActivity(i);

	}
}
