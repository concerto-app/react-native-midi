package com.shaverz.midi;

import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Looper;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MidiModule extends ReactContextBaseJavaModule implements LifecycleEventListener, MidiEventEmitter {

    private final ReactApplicationContext mContext;
    private final MidiDriverUSB mUsbDriver;
    private final Set<MidiEvent> mSubscribedEvents;
    private DeviceEventManagerModule.RCTDeviceEventEmitter mEmitter;

    public MidiModule(final ReactApplicationContext reactContext) {
        super(reactContext);
        mContext = reactContext;
        mContext.addLifecycleEventListener(this);

        // Driver needs to be opened on main thread (for UI dialog)
        mSubscribedEvents = new HashSet<MidiEvent>();
        mUsbDriver = new MidiDriverUSB(mContext, this);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                mUsbDriver.open();
            }
        });
    }

    @Override
    public void onHostResume() { }

    @Override
    public void onHostPause() { }

    @Override
    public void onHostDestroy() {
        mUsbDriver.close();
    }

    @Override
    public String getName() {
        return "Midi";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        for (MidiEvent midiEvent : MidiEvent.values()) {
            constants.put(midiEvent.name(), midiEvent.name());
        }
        return constants;
    }

    /*
         MidiEventEmitter
     */

    @Override
    public void emit(MidiEvent event, Object options) {
        if (!mContext.hasActiveCatalystInstance() || !mSubscribedEvents.contains(event))
            return;

        if (mEmitter == null)
            mEmitter = mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
        
        mEmitter.emit(event.name(), options);
    }

    @Override
    public void addListener(MidiEvent event) {
        mSubscribedEvents.add(event);
    }

    @Override
    public void removeListener(MidiEvent event) {
        mSubscribedEvents.remove(event);
    }

    @Override
    public boolean isListening(MidiEvent event) {
        return mSubscribedEvents.contains(event);
    }

    /*
        React Methods
     */

    @ReactMethod
    public void on(String midiEvent, Callback callback) {
        // Will be overriden by index.js to add NativeEventEmitter listener
        try {
            addListener(MidiEvent.valueOf(midiEvent));
            callback.invoke();
        } catch (IllegalArgumentException e) {
            callback.invoke("Failed to add listener. Invalid MidiEvent: \"" + midiEvent + "\"");
        }
    }

    @ReactMethod
    public void off(String midiEvent, Callback callback) {
        // Will be overriden by index.js to remove NativeEventEmitter listener
        try {
            removeListener(MidiEvent.valueOf(midiEvent));
            callback.invoke();
        } catch (IllegalArgumentException e) {
            callback.invoke("Failed to remove listener. Invalid MidiEvent: \"" + midiEvent + "\"");
        }
    }

    private WritableNativeMap mapDevice(UsbDevice device) {
        WritableNativeMap result = new WritableNativeMap();
        result.putInt("id", device.getDeviceId());
        result.putInt("class", device.getDeviceClass());
        result.putInt("subclass", device.getDeviceSubclass());
        result.putInt("protocol", device.getDeviceProtocol());
        result.putInt("productId", device.getProductId());
        result.putInt("vendorId", device.getVendorId());
        result.putString("name", device.getDeviceName());
        result.putString("manufacturerName", device.getManufacturerName());
        result.putString("productName", device.getProductName());
        result.putString("serialNumber", device.getSerialNumber());
        result.putString("version", device.getVersion());
        return result;
    }

    @ReactMethod
    public void getDevices(Promise promise) {
        Map<Integer, UsbDevice> deviceMap = mUsbDriver.getDevices();

        WritableNativeArray result = new WritableNativeArray();

        for (UsbDevice device: deviceMap.values()) {
            WritableNativeMap mappedDevice = this.mapDevice(device);
            result.pushMap(mappedDevice);
        }

        promise.resolve(result);
    }

    @ReactMethod
    public void getDeviceCount(Promise promise) {
        promise.resolve(mUsbDriver.getDevices().size());
    }

    @ReactMethod
    public void getDevice(int id, Promise promise) {
        UsbDevice device = mUsbDriver.getDevices().get(id);
        WritableNativeMap result = this.mapDevice(device);
        promise.resolve(result);
    }
}
