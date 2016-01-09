package com.wordpress.blogste.Protocollo_Seriale_Ardunio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;

import javax.swing.Timer;

import com.wordpress.blogste.SerialCom.Serial;

public class Protocollo extends Observable implements Observer {

	public static enum TYPE_ARDUINO {
		UNO("Uno"), DUEMILANOVE("Arduino Duemilanove w/ ATmega328");

		private String name;

		private TYPE_ARDUINO(String name) {
			this.name = name;
		}

		private String getName() {
			return name;
		}

	}

	private static final String[] MEMORY_UNO = { "A0", "A1", "A2", "A3", "A4",
			"A5" };

	public HashMap<String, Integer> memory;
	private TYPE_ARDUINO typeArduino;
	private byte INIT_BYTE = '@';
	private byte TERMINATOR_BYTE = '*';
	private byte READ = 0x0;
	private byte WRITE = 0x1;
	private int TELEGRAM_LENGHT = 10;
	private Serial serial;
	private byte buffer[] = new byte[1024];
	private byte bufferCleaned[] = new byte[1024];
	private byte telegramRX[] = new byte[10];
	private int telegramIndex = 0;
	private int bufferLast = 0;
	private boolean debug;

	public Protocollo(Serial serial, TYPE_ARDUINO type) {
		this.serial = serial;
		this.typeArduino = type;

		serial.addObserver(this);
		memory = new HashMap<String, Integer>();
		populateMemory(typeArduino);
	}

	private void populateMemory(TYPE_ARDUINO type) {
		switch (type) {
		case UNO:
			for (String s : MEMORY_UNO)
				memory.put(s, 0);
			break;
		case DUEMILANOVE:
			for (String s : MEMORY_UNO)
				memory.put(s, 0);
			break;
		}
	}

	private byte getChecksum(byte chunk[]) {
		byte checksum = 0;
		for (int i = 0; i < chunk.length - 3; i++) {
			checksum = (byte) (chunk[i] ^ chunk[i + 1]);
		}
		return checksum;

	}

	private synchronized void readBuffer() {
		int bufferLenght = serial.readBytes(buffer);
		if (debug) {
			for (int i = 0; i < bufferLenght; i++) {
				System.out.print(buffer[i]);
			}
			System.out.print("\n");
		}
		int k = 0;

		if (telegramIndex == 0) {
			while (buffer[k] != INIT_BYTE) {
				if (k < bufferLenght) {
					k++;
				} else {
					break;
				}
			}
		}

		while (k < bufferLenght) {

			if (buffer[k] == INIT_BYTE)
				telegramIndex = 0;

			telegramRX[telegramIndex++] = buffer[k++];

			if (telegramRX[telegramIndex - 1] == TERMINATOR_BYTE
					&& telegramIndex == TELEGRAM_LENGHT) {
				if (telegramRX[8] == getChecksum(telegramRX)) {
					System.arraycopy(telegramRX, 0, bufferCleaned, bufferLast,
							TELEGRAM_LENGHT);
					bufferLast += TELEGRAM_LENGHT;
				} else {
					if (debug)
						System.out.println("Error Checksum");
				}
				telegramIndex = 0;
				Arrays.fill(telegramRX, (byte) 0);
			}

			if (telegramIndex == TELEGRAM_LENGHT) {
				telegramIndex = 0;
				Arrays.fill(telegramRX, (byte) 0);
			}
		}
		analizeBuffer();
	}

	private synchronized void analizeBuffer() {
		int i = 0;
		while (i < bufferLast) {
			if (bufferCleaned[i] == INIT_BYTE) {
				i += 2;
				String key = new String(bufferCleaned, i, 1);
				i++;
				key = key + bufferCleaned[i];
				i++;
				int data = bufferCleaned[i + 3] & 0xFF
						| (bufferCleaned[i + 2] & 0xFF) << 8
						| (bufferCleaned[i + 1] & 0xFF) << 16
						| (bufferCleaned[i] & 0xFF) << 24;
				i += 4;
				memory.put(key, data);
				setChanged();
				this.notifyObservers();
			}
			i++;
		}
		bufferLast = 0;
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		readBuffer();

	}

	public synchronized void write(byte dataAddress[], byte dataValue[])
			throws IOException {
		int a = dataAddress.length;
		int b = dataValue.length;

		byte telegram[] = new byte[10];
		telegram[0] = INIT_BYTE;
		telegram[1] = WRITE;
		for (int i = 0; i < a; i++) {
			telegram[2 + i] = dataAddress[a];
		}
		for (int i = 0; i < b; i++) {
			telegram[2 + a + i] = dataValue[a];
		}
		telegram[8] = getChecksum(telegram);
		telegram[9] = TERMINATOR_BYTE;

		serial.write(telegram);
	}

	public synchronized void write(char dataType, byte dataAdress,
			int dataMemory) throws IOException {
		byte b1 = (byte) dataType;
		byte byteData[] = ByteBuffer.allocate(4).putInt(dataMemory).array();
		byte telegram[] = new byte[10];

		telegram[0] = INIT_BYTE;
		telegram[1] = WRITE;
		telegram[2] = b1;
		telegram[3] = dataAdress;
		telegram[4] = byteData[0];
		telegram[5] = byteData[1];
		telegram[6] = byteData[2];
		telegram[7] = byteData[3];
		telegram[8] = getChecksum(telegram);
		telegram[9] = TERMINATOR_BYTE;

		serial.write(telegram);
	}

	public synchronized void read(byte dataAddress[]) throws IOException {
		int a = dataAddress.length;

		byte telegram[] = new byte[10];
		telegram[0] = INIT_BYTE;
		telegram[1] = READ;
		for (int i = 0; i < a; i++) {
			telegram[2 + i] = dataAddress[a];
		}
		telegram[4] = 0;
		telegram[5] = 0;
		telegram[6] = 0;
		telegram[7] = 0;
		telegram[8] = getChecksum(telegram);
		telegram[9] = TERMINATOR_BYTE;

		serial.write(telegram);
	}

	public synchronized void read(char dataType, byte dataAdress)
			throws IOException {
		byte b1 = (byte) dataType;
		byte telegram[] = new byte[10];

		telegram[0] = INIT_BYTE;
		telegram[1] = READ;
		telegram[2] = b1;
		telegram[3] = dataAdress;
		telegram[4] = 0;
		telegram[5] = 0;
		telegram[6] = 0;
		telegram[7] = 0;
		telegram[8] = getChecksum(telegram);
		telegram[9] = TERMINATOR_BYTE;

		serial.write(telegram);
	}

	public synchronized void read(String data) throws IOException {
		byte telegram[] = new byte[10];

		telegram[0] = INIT_BYTE;
		telegram[1] = READ;
		telegram[2] = (byte) data.charAt(0);
		telegram[3] = (byte) Character.getNumericValue(data.charAt(1));
		telegram[4] = 0;
		telegram[5] = 0;
		telegram[6] = 0;
		telegram[7] = 0;
		telegram[8] = getChecksum(telegram);
		telegram[9] = TERMINATOR_BYTE;

		serial.write(telegram);
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public String[] getAddressMemory() {
		switch (typeArduino) {
		case UNO:
			return MEMORY_UNO;
		case DUEMILANOVE:
			return MEMORY_UNO;
		default:
			return null;
		}
	}

}
