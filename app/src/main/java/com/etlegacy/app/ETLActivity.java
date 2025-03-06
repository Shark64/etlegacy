package com.etlegacy.app;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.erz.joysticklibrary.JoyStick;
import com.erz.joysticklibrary.JoyStick.JoyStickListener;
import com.etlegacy.app.web.ETLDownload;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.libsdl.app.*;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;

public class ETLActivity extends SDLActivity implements JoyStickListener {

	static volatile boolean UiMenu = false;
	HashMap<String, ComponentManager.ComponentData> defaultcomponentMap;
	private static final String PREFS_NAME = "ComponentPrefs";
	private static final String COMPONENT_MAP_KEY = "componentMap";

	private ImageButton etl_console;
	private ImageButton btn;
	private ImageButton esc_btn;
	private ImageButton gears;
	private ImageButton shootBtn;
	private ImageButton reloadBtn;
	private ImageButton jumpBtn;
	private ImageButton activateBtn;
	private ImageButton altBtn;
	private ImageButton crouchBtn;
	private JoyStick moveJoystick;
	private Handler handler;
	private Runnable uiRunner;
	private Intent intent;
	private OnBackInvokedCallback callback;

	private int width;
	private int height;


	public native void sendToC(float left, float top, float width, float height);

	/**
	 * Get an uiMenu boolean variable
	 *
	 * @return UiMenu
	 */
	public static boolean getUiMenu() {
		return UiMenu;
	}

	/**
	 * Hide System UI
	 */
	private void hideSystemUI() {
		getWindow().getDecorView().setSystemUiVisibility(
			View.SYSTEM_UI_FLAG_IMMERSIVE
			// Set the content to appear under the system bars so that the
			// content doesn't resize when the system bars hide and show.
			| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
			| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			// Hide the nav bar and status bar
			| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_FULLSCREEN);
	}

	@Override
	public void finish() {
		// finishAffinity();
		finishAndRemoveTask();
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Android 13+
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			callback = new OnBackInvokedCallback() {
				@Override
				public void onBackInvoked() {
					Log.d("ETLActivity", "onBackInvoked triggered");
					SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE);
					SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
				}
			};

			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
				OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
			);

		}

		DisplayMetrics realMetrics = getResources().getDisplayMetrics();
		width = realMetrics.widthPixels;
		height = realMetrics.heightPixels;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
	}

	@Override
	protected void onDestroy() {
		// shutdown the looper and the download thread executor
		this.handler.removeCallbacks(uiRunner);
		ETLDownload.instance().shutdownExecutor();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && callback != null) {
			getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
		}

		super.onDestroy();

		// FIXME: figure out what is actually keeping this thing alive.
		System.exit(0);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		HIDDeviceManager.acquire(getContext());
		getMotionListener();
		clipboardGetText();

		setRelativeMouseEnabled(true);

		LocalBroadcastManager.getInstance(this).registerReceiver(refreshReceiver,
			new IntentFilter("REFRESH_ACTION"));

		if (isAndroidTV() || isChromebook()) {
			Log.v("ETL", "AndroidTV / ChromeBook Detected, Display UI Disabled!");
			return;
		}

		runOnUiThread(this::setupUI);
	}

	private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Refresh your data here
			runOnUiThread(ETLActivity.this::setupUI);
		}
	};

	private void RemoveLayout() {
		// Remove all Buttons!
		mLayout.removeView(etl_console);
		mLayout.removeView(btn);
		mLayout.removeView(esc_btn);
		mLayout.removeView(gears);
		mLayout.removeView(shootBtn);
		mLayout.removeView(reloadBtn);
		mLayout.removeView(jumpBtn);
		mLayout.removeView(activateBtn);
		mLayout.removeView(altBtn);
		mLayout.removeView(crouchBtn);
		mLayout.removeView(moveJoystick);
	}

	@SuppressLint({"ClickableViewAccessibility", "NonConstantResourceId"})
	private void setupUI() {
		RemoveLayout();

		LoadDefaultComponentData();

		ComponentManager.ComponentData etl_consoleData = defaultcomponentMap.get("etl_console");
		ComponentManager.ComponentData btnData = defaultcomponentMap.get("btn");
		ComponentManager.ComponentData esc_btnData = defaultcomponentMap.get("esc_btn");
		ComponentManager.ComponentData gearsData = defaultcomponentMap.get("gears");
		ComponentManager.ComponentData shootBtnData = defaultcomponentMap.get("shootBtn");
		ComponentManager.ComponentData reloadBtnData = defaultcomponentMap.get("reloadBtn");
		ComponentManager.ComponentData jumpBtnData = defaultcomponentMap.get("jumpBtn");
		ComponentManager.ComponentData activateBtnData = defaultcomponentMap.get("activateBtn");
		ComponentManager.ComponentData altBtnData = defaultcomponentMap.get("altBtn");
		ComponentManager.ComponentData crouchBtnData = defaultcomponentMap.get("crouchBtn");
		ComponentManager.ComponentData moveJoystickData = defaultcomponentMap.get("moveJoystick");

		assert etl_consoleData != null;
		assert btnData != null;
		assert esc_btnData != null;
		assert gearsData != null;
		assert shootBtnData != null;
		assert reloadBtnData != null;
		assert jumpBtnData != null;
		assert activateBtnData != null;
		assert altBtnData != null;
		assert crouchBtnData != null;
		assert moveJoystickData != null;

		// Create ImageButtons
		etl_console = new ImageButton(this);
		etl_console.setId(View.generateViewId());
		etl_console.setImageResource(etl_consoleData.resourceId);
		etl_console.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			etl_console.setBackgroundResource(R.drawable.border_drawable);

		btn = new ImageButton(this);
		btn.setId(View.generateViewId());
		btn.setImageResource(btnData.resourceId);
		btn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			btn.setBackgroundResource(R.drawable.border_drawable);

		esc_btn = new ImageButton(this);
		esc_btn.setId(View.generateViewId());
		esc_btn.setImageResource(esc_btnData.resourceId);
		esc_btn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			esc_btn.setBackgroundResource(R.drawable.border_drawable);

		gears = new ImageButton(this);
		gears.setId(View.generateViewId());
		gears.setImageResource(gearsData.resourceId);
		gears.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			gears.setBackgroundResource(R.drawable.border_drawable);

		shootBtn = new ImageButton(this);
		shootBtn.setId(View.generateViewId());
		shootBtn.setImageResource(shootBtnData.resourceId);
		shootBtn.setBackgroundColor(0x00000000);
		shootBtn.setClickable(false);
		shootBtn.setFocusable(false);
		if (BuildConfig.DEBUG)
			shootBtn.setBackgroundResource(R.drawable.border_drawable);

		reloadBtn = new ImageButton(this);
		reloadBtn.setId(View.generateViewId());
		reloadBtn.setImageResource(reloadBtnData.resourceId);
		reloadBtn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			reloadBtn.setBackgroundResource(R.drawable.border_drawable);

		jumpBtn = new ImageButton(this);
		jumpBtn.setId(View.generateViewId());
		jumpBtn.setImageResource(jumpBtnData.resourceId);
		jumpBtn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			jumpBtn.setBackgroundResource(R.drawable.border_drawable);

		activateBtn = new ImageButton(this);
		activateBtn.setId(View.generateViewId());
		activateBtn.setImageResource(activateBtnData.resourceId);
		activateBtn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			activateBtn.setBackgroundResource(R.drawable.border_drawable);

		altBtn = new ImageButton(this);
		altBtn.setId(View.generateViewId());
		altBtn.setImageResource(altBtnData.resourceId);
		altBtn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			altBtn.setBackgroundResource(R.drawable.border_drawable);

		crouchBtn = new ImageButton(this);
		crouchBtn.setId(View.generateViewId());
		crouchBtn.setImageResource(crouchBtnData.resourceId);
		crouchBtn.setBackgroundColor(0x00000000);
		if (BuildConfig.DEBUG)
			crouchBtn.setBackgroundResource(R.drawable.border_drawable);

		moveJoystick = new JoyStick(getApplicationContext());
		moveJoystick.setListener(ETLActivity.this);
		moveJoystick.setId(View.generateViewId());
		moveJoystick.setBackgroundResource(moveJoystickData.resourceId);
		moveJoystick.setPadColor(Color.TRANSPARENT);
		moveJoystick.setButtonColor(Color.TRANSPARENT);
		moveJoystick.setButtonRadiusScale(50);
		if (BuildConfig.DEBUG) {
			moveJoystick.setBackgroundResource(R.drawable.border_drawable);
			moveJoystick.setPadColor(Color.WHITE);
		}

		Object[][] viewsWithParams = {
			{etl_console, etl_consoleData.width, etl_consoleData.height, etl_consoleData.gravity, etl_consoleData.margins, etl_consoleData.resourceId},
			{btn, btnData.width, btnData.height, btnData.gravity, btnData.margins, btnData.resourceId},
			{esc_btn, esc_btnData.width, esc_btnData.height, esc_btnData.gravity, esc_btnData.margins, esc_btnData.resourceId},
			{gears, gearsData.width, gearsData.height, gearsData.gravity, gearsData.margins, gearsData.resourceId},
			{shootBtn, shootBtnData.width, shootBtnData.height, shootBtnData.gravity, shootBtnData.margins, shootBtnData.resourceId},
			{reloadBtn, reloadBtnData.width, reloadBtnData.height, reloadBtnData.gravity, reloadBtnData.margins, reloadBtnData.resourceId},
			{jumpBtn, jumpBtnData.width, jumpBtnData.height, jumpBtnData.gravity, jumpBtnData.margins, jumpBtnData.resourceId},
			{activateBtn, activateBtnData.width, activateBtnData.height, activateBtnData.gravity, activateBtnData.margins, activateBtnData.resourceId},
			{altBtn, altBtnData.width, altBtnData.height, altBtnData.gravity, altBtnData.margins, altBtnData.resourceId},
			{crouchBtn, crouchBtnData.width, crouchBtnData.height, crouchBtnData.gravity, crouchBtnData.margins, crouchBtnData.resourceId},
			{moveJoystick, moveJoystickData.width, moveJoystickData.height, moveJoystickData.gravity, moveJoystickData.margins, moveJoystickData.resourceId}
		};

		// Iterate over the array to create and set LayoutParams
		for (Object[] entry : viewsWithParams) {
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				(int) entry[1], // Width
				(int) entry[2]  // Height
			);

			// Set gravity
			params.gravity = (int) entry[3];

			// Set margins
			int[] margins = (int[]) entry[4];
			params.setMargins(margins[0], margins[1], margins[2], margins[3]);

			// Assign the LayoutParams to the view
			((android.view.View) entry[0]).setLayoutParams(params);
		}

		// Add listeners
		etl_console.setOnClickListener(v -> {
			onNativeKeyDown(68);
			onNativeKeyUp(68);
		});

		btn.setOnClickListener(v -> {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
		});

		esc_btn.setOnClickListener(v -> {
			onNativeKeyDown(111);
			onNativeKeyUp(111);
		});

		gears.setOnClickListener(this::showPopupWindow);

		// Send margins of the shootBtn to C
		sendToC(shootBtnData.margins[0], shootBtnData.margins[1],  shootBtnData.margins[0] + shootBtnData.width,  shootBtnData.margins[1] + shootBtnData.height);

		reloadBtn.setOnClickListener(v -> {
			onNativeKeyDown(46);
			onNativeKeyUp(46);
		});

		jumpBtn.setOnTouchListener((v, event) -> {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					onNativeKeyDown(62);
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					onNativeKeyUp(62);
					break;
			}
			return false;
		});

		activateBtn.setOnClickListener(v -> {
			onNativeKeyDown(34);
			onNativeKeyUp(34);
		});

		altBtn.setOnClickListener(v -> {
			onNativeKeyDown(30);
			onNativeKeyUp(30);
		});

		crouchBtn.setOnTouchListener((v, event) -> {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					onNativeKeyDown(31);
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					onNativeKeyUp(31);
					break;
			}
			return false;
		});

		// Add buttons to layout
		mLayout.addView(etl_console);
		mLayout.addView(btn);
		mLayout.addView(esc_btn);
		mLayout.addView(gears);
		mLayout.addView(shootBtn);
		mLayout.addView(reloadBtn);
		mLayout.addView(jumpBtn);
		mLayout.addView(activateBtn);
		mLayout.addView(altBtn);
		mLayout.addView(crouchBtn);
		mLayout.addView(moveJoystick);

		handler = new Handler(Looper.getMainLooper());
		uiRunner = () -> {
			Log.d("LOOPPER", "Running");
			int delay = runUI();
			if (delay > 0) {
				handler.postDelayed(uiRunner, delay);
			}
		};
		handler.post(uiRunner);
	}

	private void showPopupWindow(View anchorView) {
		View popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu_layout, null);

		// Create PopupWindow with specific size
		PopupWindow popupWindow = new PopupWindow(popupView,
			WindowManager.LayoutParams.WRAP_CONTENT,
			WindowManager.LayoutParams.WRAP_CONTENT,
			true);

		// Set background with rounded corners
		popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		popupWindow.setOutsideTouchable(true);
		popupWindow.setElevation(10f);


		// Show the popup window in the center of the screen
		popupWindow.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER, 0, 0);

		// Dim background
		dimBackground(0.5f);

		// Handle item clicks
		TextView ui_editor = popupView.findViewById(R.id.ui_editor);
		TextView theme_editor = popupView.findViewById(R.id.theme_editor);
		TextView delete = popupView.findViewById(R.id.delete);

		ui_editor.setOnClickListener(v -> {
			intent = new Intent(ETLActivity.this, SetupUIPositionActivity.class);
			startActivityForResult(intent, 1);
			Toast.makeText(this, "Opened UI Editor", Toast.LENGTH_SHORT).show();
			popupWindow.dismiss();
		});

		theme_editor.setOnClickListener(v -> {
			intent = new Intent(ETLActivity.this, SetupUIThemeActivity.class);
			startActivityForResult(intent, 1);
			Toast.makeText(this, "Opened Theme Editor", Toast.LENGTH_SHORT).show();
			popupWindow.dismiss();
		});

		delete.setOnClickListener(v -> {
			DeleteComponentData();
			Toast.makeText(this, "Deleted Saved Positions", Toast.LENGTH_SHORT).show();
			popupWindow.dismiss();
		});

		// Restore background when popup dismisses
		popupWindow.setOnDismissListener(() -> dimBackground(1.0f));
	}

	private void dimBackground(float dimAmount) {
		WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
		layoutParams.alpha = dimAmount;
		getWindow().setAttributes(layoutParams);
	}

	@SuppressLint("RtlHardcoded")
	private void LoadDefaultComponentData() {
		SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		Gson gson = new Gson();
		String json = sharedPreferences.getString(COMPONENT_MAP_KEY, null);
		Log.v("ETLActivity", "Loaded JSON: " + json);
		Type type = new TypeToken<HashMap<String, ComponentManager.ComponentData>>() {}.getType();
		defaultcomponentMap = gson.fromJson(json, type);

		if (defaultcomponentMap == null) {
			Log.v("ETLActivity", "LoadDefaultComponentData: " + null);
			// If no data is found, initialize with default values
			defaultcomponentMap = new HashMap<>();
			defaultcomponentMap.put("etl_console", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{0, 0, 0, 0}, R.drawable.ic_one_line));
			defaultcomponentMap.put("btn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.1), 0, 0, 0}, R.drawable.ic_keyboard));
			defaultcomponentMap.put("esc_btn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.2), 0, 0, 0}, R.drawable.ic_escape));
			defaultcomponentMap.put("gears", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.55), 0, 0, 0}, R.drawable.gears));
			defaultcomponentMap.put("shootBtn", new ComponentManager.ComponentData(950, 600, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.6), (int)(height * 0.25), 0, 0}, R.drawable.ic_shoot));
			defaultcomponentMap.put("reloadBtn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 1.0), 0, 0, 0}, R.drawable.ic_reload));
			defaultcomponentMap.put("jumpBtn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.55), (int)(height * 0.9), 0, 0}, R.drawable.ic_jump));
			defaultcomponentMap.put("activateBtn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.1), (int)(height * 0.9), 0, 0}, R.drawable.ic_use));
			defaultcomponentMap.put("altBtn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.95), (int)(height * 0.9), 0, 0}, R.drawable.ic_alt));
			defaultcomponentMap.put("crouchBtn", new ComponentManager.ComponentData(100, 100, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 1.0), (int)(height * 0.9), 0, 0}, R.drawable.ic_crouch));
			defaultcomponentMap.put("moveJoystick", new ComponentManager.ComponentData(400, 400, Gravity.TOP | Gravity.LEFT, new int[]{(int)(width * 0.05), (int)(height * 0.3), 0, 0}, 0));

			SaveComponentData();
		} else {
			LoadComponentData();
		}
	}

	private void DeleteComponentData() {
		SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(COMPONENT_MAP_KEY); // Remove the component data
		editor.apply();

		defaultcomponentMap.clear();
		defaultcomponentMap = null;
		Log.v("ETLActivity", "DeleteComponentData: Data deleted successfully");
		setupUI();
	}

	private void SaveComponentData() {
		SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		Gson gson = new Gson();
		String json = gson.toJson(defaultcomponentMap);
		editor.putString(COMPONENT_MAP_KEY, json);
		editor.apply();
		Log.v("ETLActivity", "SaveComponentData: Data saved successfully");
	}

	// Call this whenever you modify the defaultcomponentMap
	public void UpdateComponentData(String componentKey, ComponentManager.ComponentData newData) {
		defaultcomponentMap.put(componentKey, newData);
		SaveComponentData();
	}

	private void LoadComponentData() {
		SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		Gson gson = new Gson();
		String json = sharedPreferences.getString(COMPONENT_MAP_KEY, null);
		Type type = new TypeToken<HashMap<String, ComponentManager.ComponentData>>() {}.getType();
		defaultcomponentMap = gson.fromJson(json, type);
		assert defaultcomponentMap != null;
		for (String key : defaultcomponentMap.keySet()) {
			UpdateComponentData(key, defaultcomponentMap.get(key));
		}
		Log.v("ETLActivity", "LoadComponentData: " + defaultcomponentMap.toString());
	}

	@SuppressLint("ClickableViewAccessibility")
	private int runUI() {
		if (!getUiMenu()) {
			setViewVisibility(false, reloadBtn, jumpBtn, activateBtn, altBtn, crouchBtn, moveJoystick);
			return 500;
		}
		setViewVisibility(true, reloadBtn, jumpBtn, activateBtn, altBtn, crouchBtn, moveJoystick);
		return 2000;
	}

	private void setViewVisibility(boolean isVisible, View... views) {
		for (View view : views) {
			view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		if (hasFocus) {
			hideSystemUI();
		}
		super.onWindowFocusChanged(hasFocus);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		// Check that the event came from a game controller
		if (((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
			(event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) && event.getAction() == MotionEvent.ACTION_MOVE) {
			SDLControllerManager.handleJoystickMotionEvent(event);
			return true;
		}
		return super.onGenericMotionEvent(event);
	}

	@Override
	public void onMove(JoyStick joyStick, double angle, double power, int direction) {

		if (isAndroidTV() || isChromebook()) {
			Log.d("MOVE", "Ignoring onMove, since the device not a mobile device");
			return;
		}

		switch (direction) {
			case JoyStick.DIRECTION_CENTER:
				onNativeKeyUp(51);
				onNativeKeyUp(32);
				onNativeKeyUp(47);
				onNativeKeyUp(29);
				break;
			case JoyStick.DIRECTION_UP:
				onNativeKeyUp(32);
				onNativeKeyUp(47);
				onNativeKeyUp(29);
				onNativeKeyDown(51);
				break;
			case JoyStick.DIRECTION_UP_RIGHT:
				onNativeKeyUp(47);
				onNativeKeyUp(29);
				onNativeKeyDown(51);
				onNativeKeyDown(32);
				break;
			case JoyStick.DIRECTION_RIGHT:
				onNativeKeyUp(51);
				onNativeKeyUp(47);
				onNativeKeyUp(29);
				onNativeKeyDown(32);
				break;
			case JoyStick.DIRECTION_RIGHT_DOWN:
				onNativeKeyUp(51);
				onNativeKeyUp(29);
				onNativeKeyDown(32);
				onNativeKeyDown(47);
				break;
			case JoyStick.DIRECTION_DOWN:
				onNativeKeyUp(51);
				onNativeKeyUp(32);
				onNativeKeyUp(29);
				onNativeKeyDown(47);
				break;
			case JoyStick.DIRECTION_DOWN_LEFT:
				onNativeKeyUp(51);
				onNativeKeyUp(32);
				onNativeKeyDown(47);
				onNativeKeyDown(29);
				break;
			case JoyStick.DIRECTION_LEFT:
				onNativeKeyUp(51);
				onNativeKeyUp(32);
				onNativeKeyUp(47);
				onNativeKeyDown(29);
				break;
			case JoyStick.DIRECTION_LEFT_UP:
				onNativeKeyUp(32);
				onNativeKeyUp(47);
				onNativeKeyDown(29);
				onNativeKeyDown(51);
				break;
		}
	}

	@Override
	public void onTap() {

	}

	@Override
	public void onDoubleTap() {

	}

	@Override
	protected String[] getArguments() {
		return new String[]{
			String.valueOf(Paths.get((Objects.requireNonNull(getExternalFilesDir(null)).toPath() + "/etlegacy"))),
		};
	}

	@Override
	protected String[] getLibraries() {
		return new String[]{
			"etl"
		};
	}

}
