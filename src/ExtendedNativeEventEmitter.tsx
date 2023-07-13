import { NativeEventEmitter, type NativeModule } from 'react-native';
import {
  Constants as DeviceScanner,
  type DeviceFoundPayload,
} from './definitions/DeviceScanner';

export default class ExtendedNativeEventEmitter extends NativeEventEmitter {
  constructor(nativeModule?: NativeModule) {
    super(nativeModule);
  }

  onEvents(listener: (even: string, payload: DeviceFoundPayload) => void) {
    [
      DeviceScanner.EVENT_START_SCAN,
      DeviceScanner.EVENT_STOP_SCAN,
      DeviceScanner.EVENT_DEVICE_FOUND,
      DeviceScanner.EVENT_OTHER,
      DeviceScanner.EVENT_ERROR,
    ].forEach((eventName) => {
      this.addListener(eventName, (...args: any[]) => {
        listener.apply(this, [eventName, args[0]]);
      });
    });
  }

  offEvents() {
    [
      DeviceScanner.EVENT_START_SCAN,
      DeviceScanner.EVENT_STOP_SCAN,
      DeviceScanner.EVENT_DEVICE_FOUND,
      DeviceScanner.EVENT_OTHER,
      DeviceScanner.EVENT_ERROR,
    ].forEach((eventName) => {
      this.removeAllListeners(eventName);
    });
  }
}