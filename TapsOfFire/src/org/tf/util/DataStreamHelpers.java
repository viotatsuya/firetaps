/*
 * Taps of Fire
 * Copyright (C) 2009 Dmitry Skiba
 * 
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package org.tf.util;

import java.io.DataInput;
import java.io.IOException;

public class DataStreamHelpers {
	
	public static void checkTag(DataInput input,int expectedTag) throws IOException {
		int tag=input.readInt();
		if (tag!=expectedTag) {
			throw new IOException(String.format(
				"Invalid tag %08X (expecting %08X).",tag,expectedTag
			));
		}
	}
	
	public static IOException inconsistentStateException() {
		return new IOException("Inconsistent state.");
	}
}
