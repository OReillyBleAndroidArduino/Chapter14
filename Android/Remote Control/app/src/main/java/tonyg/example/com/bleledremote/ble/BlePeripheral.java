package tonyg.example.com.bleledremote.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * This class allows us to share Bluetooth resources
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2016-03-06
 */
public class BlePeripheral {
    private static final String TAG = BlePeripheral.class.getSimpleName();

    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic;
    private Context mContext;

    /** Bluetooth Stuff **/
    public static final String BROADCAST_NAME = "LedRemote";
    public static final UUID SERVICE_UUID = UUID.fromString("0000180c-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
    // this is the UUID of the descriptor used to enable and disable notifications
    public static final UUID CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /** Data packet **/
    private static final int TRANSMISSION_LENGTH = 2;
    private static final byte FOOTER_POSITION = TRANSMISSION_LENGTH - 1;
    private static final byte DATA_POSITION = 0;

    /** Sending commands **/
    public static final byte COMMAND_LED_OFF = 1;
    public static final byte COMMAND_LED_ON = 2;

    /** Receiving messages **/
    public static final byte MESSAGE_TYPE_ERROR = 0;
    public static final byte MESSAGE_TYPE_CONFIRMATION = 1;
    public static final byte MESSAGE_TYPE_COMMAND = 2;
    public static final int LED_STATE_ERROR = 1;
    public static final int LED_STATE_ON = 1;
    public static final int LED_STATE_OFF = 2;


    public BlePeripheral(Context context) {
        mContext = context;
    }

    public void setCharacteristic(BluetoothGattCharacteristic characteristic) {
        mCharacteristic = characteristic;
    }

    /**
     * Determine if the incoming value is a command, confirmation, or error
     *
     * @param value the incoming data value
     * @return integer message type.  See @MESSAGE_TYPE_ERROR, @MESSAGE_TYPE_CONFIRMATION, and @MESSAGE_TYPE_COMMAND
     * @throws Exception
     */
    public int getMessageType(byte[] value)  throws Exception {
        byte dataFooter = value[FOOTER_POSITION];
        int returnValue = LED_STATE_ERROR;
        if (dataFooter == MESSAGE_TYPE_CONFIRMATION) {
            returnValue = value[DATA_POSITION];
        }
        return returnValue;
    }


    /**
     * Connect to a Peripheral
     *
     * @param bluetoothDevice the Bluetooth Device
     * @param callback The connection callback
     * @return a connection to the BluetoothGatt
     * @throws Exception if no device is given
     */
    public BluetoothGatt connect(BluetoothDevice bluetoothDevice, BluetoothGattCallback callback) throws Exception {
        if (bluetoothDevice == null) {
            throw new Exception("No bluetooth device provided");
        }
        mBluetoothDevice = bluetoothDevice;
        mBluetoothGatt = bluetoothDevice.connectGatt(mContext, false, callback);
        refreshDeviceCache();
        return mBluetoothGatt;
    }

    /**
     * Disconnect from a Peripheral
     */
    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    /**
     * A connection can only close after a successful disconnect.
     * Be sure to use the BluetoothGattCallback.onConnectionStateChanged event
     * to notify of a successful disconnect
     */
    public void close() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close(); // close connection to Peripheral
            mBluetoothGatt = null; // release from memory
        }
    }
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }


    /**
     * Clear the GATT Service cache.
     *
     * New in this chapter
     *
     * @return <b>true</b> if the device cache clears successfully
     * @throws Exception
     */
    public boolean refreshDeviceCache() throws Exception {
        Method localMethod = mBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
        if (localMethod != null) {
            boolean bool = ((Boolean) localMethod.invoke(mBluetoothGatt, new Object[0])).booleanValue();
            return bool;
        }

        return false;
    }

    /**
     * Request a data/value read from a Ble Characteristic
     *
     * @param characteristic
     */
    public void readValueFromCharacteristic(final BluetoothGattCharacteristic characteristic) {
        // Reading a characteristic requires both requesting the read and handling the callback that is
        // sent when the read is successful
        // http://stackoverflow.com/a/20020279
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Turn the remote LED on;
     */
    public void turnLedOn() {
        writeCommandToCharacteristic(COMMAND_LED_ON, mCharacteristic);
    }

    /**
     * Turn the remote LED off.
     */
    public void turnLedOff() {
        writeCommandToCharacteristic(COMMAND_LED_OFF, mCharacteristic);
    }

    /**
     * Convert bytes to a hexadecimal String
     *
     * @param bytes a byte array
     * @return hexadecimal string
     */
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Write a value to a Characteristic
     *
     * @param command The command being written
     * @param characteristic The Characteristic being written to
     * @throws Exception
     */
    public void writeCommandToCharacteristic(byte command, BluetoothGattCharacteristic characteristic) {
        // build data packet
        byte[] data = new byte[TRANSMISSION_LENGTH];
        data[DATA_POSITION] = command;
        data[FOOTER_POSITION] = MESSAGE_TYPE_COMMAND;


        Log.d(TAG, "Writing Message: "+bytesToHex(data));

        characteristic.setValue(data);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }



    /**
     * Subscribe or unsubscribe from Characteristic Notifications
     *
     * New in this chapter
     *
     * @param characteristic
     * @param enableNotifications <b>true</b> for "subscribe" <b>false</b> for "unsubscribe"
     */
    public void setCharacteristicNotification(final BluetoothGattCharacteristic characteristic, final boolean enableNotifications) {
        // modified from http://stackoverflow.com/a/18011901/5671180
        // This is a 2-step process
        // Step 1: set the Characteristic Notification parameter locally
        mBluetoothGatt.setCharacteristicNotification(characteristic, enableNotifications);
        // Step 2: Write a descriptor to the Bluetooth GATT enabling the subscription on the Perpiheral
        // turns out you need to implement a delay between setCharacteristicNotification and setvalue.
        // maybe it can be handled with a callback, but this is an easy way to implement
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);

                if (enableNotifications) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                }
                mBluetoothGatt.writeDescriptor(descriptor);
            }
        }, 10);


    }


    /**
     * Check if a Characetristic supports write permissions
     * @return Returns <b>true</b> if property is writable
     */
    public static boolean isCharacteristicWritable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    /**
     * Check if a Characetristic has read permissions
     *
     * @return Returns <b>true</b> if property is Readable
     */
    public static boolean isCharacteristicReadable(BluetoothGattCharacteristic characteristic) {
        return ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }

    /**
     * Check if a Characteristic supports Notifications
     *
     * @return Returns <b>true</b> if property is supports notification
     */
    public static boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }






}
