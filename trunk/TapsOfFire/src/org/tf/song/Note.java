package org.tf.song;

public class Note
{
	

	public Note(int string,long time,long endTime) {
		m_string=string;
		m_sTime = time;
		m_endTime = endTime;
	}
	
	public int getString() {
		return m_string;
	}
	public void setString(int string)
	{
		m_string = string;
	}
	public long getEndTime()
	{
		return m_endTime;
	}
	public void setEndTime( long time )
	{
		m_endTime = time;
	}
	public long getStartTime()
	{
		return m_sTime;
	}
	public void setStartTime(long time)
	{
		m_sTime = time;
	}
	private long m_sTime;
	private long m_endTime;
	private int m_string;

}
