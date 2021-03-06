package com.arcao.geocaching4locus.util;

public class Coordinates {
	public static String convertDoubleToDeg(double source, boolean isLon) {
		return convertDoubleToDeg(source, isLon, 3);
	}

	public static String convertDoubleToDeg(double source, boolean isLon, int precision) {
		StringBuffer sb = new StringBuffer();
		if (source < 0) {
			sb.append((!isLon) ? 'S' : 'W');
			source = -source;
		} else {
			sb.append((!isLon) ? 'N' : 'E');
		}
		sb.append(' ');

		int deg = (int) source;

		// FIX for rounding errors
		double min = roundDouble(((source - deg) * 60D), precision);
		if (min == 60D) {
			deg++;
			min = 0D;
		}

		sb.append(deg);
		sb.append("\u00B0 ");
		sb.append(round(min, precision));

		return sb.toString();
	}

	public static double convertDegToDouble(String source) {
		String tmp = source.trim().replace(',', '.');

		int index = 0;
		int end = 0;

		char ch = ' ';

		double deg = 0D;
		double min = 0D;
		double sec = 0D;

		double direction = 1;

		try {
			ch = Character.toUpperCase(tmp.charAt(index));
			if (ch == 'S' || ch == 'W' || ch == '-') {
				direction = -1;
				index++;
			}
			if (ch == 'N' || ch == 'E' || ch == '+')
				index++;

			while (!Character.isDigit(tmp.charAt(index)))
				index++;
			end = getDoubleNumberEnd(tmp, index);
			deg = Float.parseFloat(tmp.substring(index, end));
			index = end;

			while (index < tmp.length() && !Character.isDigit(tmp.charAt(index)))
				index++;
			if (index < tmp.length()) {
				end = getDoubleNumberEnd(tmp, index);
				min = Double.parseDouble(tmp.substring(index, end));
				index = end;

				while (index < tmp.length() && !Character.isDigit(tmp.charAt(index)))
					index++;
				if (index < tmp.length()) {
					end = getDoubleNumberEnd(tmp, index);
					sec = Double.parseDouble(tmp.substring(index, end));
					index = end;
				}
			}

			return direction * (deg + (min / 60D) + (sec / 3600D));
		} catch (Exception e) {
			return Float.NaN;
		}
	}

	private static int getDoubleNumberEnd(String source, int start) {
		for (int i = start; i < source.length(); i++) {
			if (!Character.isDigit(source.charAt(i)) && source.charAt(i) != '.') {
				return i;
			}
		}
		return source.length();
	}

	public static double roundDouble(double source, int decimalPlaces) {
	    double multipicationFactor = Math.pow(10, decimalPlaces);
	    double sourceMultipled = source * multipicationFactor;
	    return Math.round(sourceMultipled) / multipicationFactor;
	}

	public static String round(double source, int decimals) {
		if (decimals < 0)
			throw new IllegalArgumentException("decimals must be great or equal to zero");

		if (decimals == 0) {
			return Long.toString((long) source);
		}

		double rounded = roundDouble(source, decimals);

		String val = Double.toString(rounded);
		int dot = val.indexOf('.');
		if (dot == -1) {
			StringBuffer sb = new StringBuffer(val);
			sb.append('.');
			for (int i = 0; i < decimals; i++)
				sb.append('0');
			return sb.toString();
		} else {
			if (val.length() - (dot + decimals) > 0) {
				return val.substring(0, dot + decimals + 1);
			}
			StringBuffer sb = new StringBuffer(val);
			for (int i = val.length(); i <= dot + decimals; i++)
				sb.append('0');
			return sb.toString();
		}
	}
}
