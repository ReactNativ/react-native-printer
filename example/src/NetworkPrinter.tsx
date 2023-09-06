import * as React from 'react';

import { Text } from 'react-native';

import {
  RNPrinter,
  DeviceScanner,
  DeviceScannerEventEmitter,
  RNPrinterEventEmitter,
  JobBuilder,
} from '@decky.fx/react-native-printer';
import type {
  DeviceScanEventPayload,
  DeviceData,
} from '@decky.fx/react-native-printer/DeviceScanner';
import type { RNPrinterEventPayload } from '@decky.fx/react-native-printer/RNPrinter';

import Row from './Row';
import Button from './Button';

const NetworkPrinter = () => {
  const [address, setAddress] = React.useState<string | undefined>('');
  const [port, setPort] = React.useState<number | undefined>(0);

  const scan = async () => {
    RNPrinterEventEmitter.onEvents(
      (event: string, payload: RNPrinterEventPayload) => {
        console.log('RNPrinterEventEmitter', event, payload);
      }
    );
    DeviceScannerEventEmitter.onEvents(
      (event: string, payload: DeviceScanEventPayload) => {
        if (
          event === DeviceScanner.EVENT_ERROR &&
          payload?.message?.startsWith('Unable to connect')
        ) {
          return;
        }
        console.log('DeviceScannerEventEmitter', event, payload);
        if (event === 'DEVICE_FOUND') {
          const device = payload as DeviceData;
          setAddress(device.address);
          setPort(device.port);
        }
      }
    );

    const allowed = await RNPrinter.checkPermissions(
      DeviceScanner.SCAN_NETWORK
    );
    if (!allowed) {
      RNPrinter.requestPermissions(DeviceScanner.SCAN_NETWORK);
      return;
    }
    DeviceScanner.scan(DeviceScanner.SCAN_NETWORK);
  };

  const print = async () => {
    if (address) {
      const jobId = await JobBuilder.begin();
      await JobBuilder.selectPrinter(jobId, {
        connection: RNPrinter.PRINTER_CONNECTION_NETWORK,
        address: address,
        port: port,
        width: RNPrinter.PRINTING_WIDTH_76_MM,
        maxChars: RNPrinter.PRINTING_LINES_MAX_CHAR_40,
      });
      await JobBuilder.initializePrinter(jobId);
      const designs = RNPrinter.TEST_PRINT_DESIGN.split('\n');
      for (let i = 0; i < designs.length; i++) {
        let line = designs[i]!!;
        await JobBuilder.printLine(jobId, line);
      }
      /*
      await JobBuilder.feedPaper(jobId, 20);
      await JobBuilder.printLine(jobId, '------------------');
      await JobBuilder.feedPaper(jobId, 20);
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.feedPaper(jobId, 20);
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.feedPaper(jobId, 20);
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.printLine(jobId, '--------Sesuatu----------');
      await JobBuilder.feedPaper(jobId, 20);
      await JobBuilder.printLine(jobId, '--------Last----------');
      await JobBuilder.feedPaper(jobId, 100);
      await JobBuilder.printLine(jobId, ' ');
      await JobBuilder.printLine(jobId, ' ');
      await JobBuilder.printLine(jobId, ' ');
      await JobBuilder.printLine(jobId, ' ');
      await JobBuilder.printLine(jobId, ' ');
      await JobBuilder.printLine(jobId, ' ');
      */
      await JobBuilder.cutPaper(jobId);
      const job = await JobBuilder.build(jobId);
      RNPrinter.enqueuePrint(job);
    }
  };

  const print2 = async () => {
    if (address) {
      RNPrinter.enqueuePrint2(
        {
          connection: RNPrinter.PRINTER_CONNECTION_NETWORK,
          address: address,
        },
        RNPrinter.TEST_PRINT_DESIGN,
        true,
        true
      );
    }
  };

  const stop = async () => {
    DeviceScanner.stop(DeviceScanner.SCAN_NETWORK);
  };

  React.useEffect(() => {
    return () => {
      RNPrinterEventEmitter.offEvents();
      DeviceScannerEventEmitter.offEvents();
    };
  }, []);

  return (
    <Row>
      <Button text="Scan Network Devices" onClick={scan} />
      <Text>{address && port ? `${address}:${port}` : ''}</Text>
      <Button text="Print" onClick={print} />
      <Button text="Print (Deprecated)" onClick={print2} />
      <Button text="Stop Scan" onClick={stop} />
    </Row>
  );
};
export default NetworkPrinter;
