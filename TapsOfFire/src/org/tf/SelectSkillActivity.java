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
package org.tf;

import java.io.File;
import java.io.IOException;

import org.tf.player.Vorbis2RawConverter;
import org.tf.song.Song;
import org.tf.song.SongCache;
import org.tf.song.SongDB;
import org.tf.song.SongInfo;
import org.tf.song.SongIni;
import org.tf.stage.SongPlayer;
import org.tf.ui.ActivityBase;
import org.tf.ui.PlayableSkillView;
import org.tf.ui.UIHelpers;
import org.tf.ui.UISoundEffects;
import org.tf.util.AssetExtractor;
import org.tf.util.DataInputBA;
import org.tf.util.DataOutputBA;
import org.tf.util.MiscHelpers;
import org.tf.R;

import com.mobclix.android.sdk.MobclixMMABannerXLAdView;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class SelectSkillActivity extends ActivityBase implements PlayableSkillView.Callback {
	
	protected void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		
		
		setContentView(R.layout.select_skill);
		
		MobclixMMABannerXLAdView banner_adview = (MobclixMMABannerXLAdView) findViewById(R.id.banner_adview);
		
		try
		{
			banner_adview.getAd();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		usePageFlipper(savedState);
		
		Intent intent=getIntent();
		loadSong(intent.getByteArrayExtra(SongInfo.BUNDLE_KEY));
	
//		try {
//			m_song=new SongInfo(
//					getAssets(),new File("songs/api"));
//					//new File("/sdcard/API-no-song"));
//			MiscHelpers.cleanup(Config.getSongCachePath());
//		}
//		catch (org.tof.song.InvalidSongException e) {
//			throw new RuntimeException(e);
//		}
		
		
		initializeSkillViews();
		
		UIHelpers.setText(this,R.id.name,m_song.getName());
		UIHelpers.setText(this,R.id.artist,m_song.getArtist());
		UISoundEffects.playInSound();
		
		//example_adview = (AdView) findViewById(R.id.ad);
		//example_adview.setVisibility(AdView.VISIBLE);
	}
	
	protected void onResume() {
		super.onResume();
		SongDB.load(this);
		setupSkillViews();
		doPageAction(getCurrentPage(),PAGEACTION_RESUME);
		if (getCurrentPage()==PAGE_MAIN) {
			animate();
		}
	}
	
	/////////////////////////////////////////////////////// logic
	
	private void loadSong(byte[] song) {
		try {
			m_song=new SongInfo(new DataInputBA(song));
			m_originalSong=new SongInfo(m_song);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void onPlaySkill(int skill) {
		m_song.setSelectedSkill(skill);
		prepareSong();
	}
	
	private void prepareSong() {
		if (m_song.isAsset()) {
			if (!checkSDCard()) {
				flipToPage(PAGE_SDCARD,true);
				return;
			}
			if (checkExtracted()) {
				playSong();
			} else {
				flipToPage(PAGE_EXTRACTOR,true);
			}
		} else {
			if (checkConverted()) {
				playSong();
			} else {
				flipToPage(PAGE_CONVERTER,true);
			}
		}
	}
	
	private void playSong() {
		try {
			DataOutputBA dataOut=new DataOutputBA();
			m_song.saveState(dataOut);
			m_song=new SongInfo(m_originalSong);
			Intent intent=new Intent(this,GameActivity.class);
			intent.putExtra(SongInfo.BUNDLE_KEY,dataOut.toByteArray());
			startActivity(intent);
			flipToPage(PAGE_MAIN,false);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void animate() {
		int offset=UIHelpers.startViewAnimation(
			this,
			R.id.head,R.anim.head_in);
		animateSkillViews(offset);
	}
	
	/////////////////////////////////////////////////////// sdcard
	
	private boolean checkSDCard() {
		return Environment.getExternalStorageState().equals(
			Environment.MEDIA_MOUNTED);
	}
	
	private void onSDCardPageAction(int action) {
		if (action==PAGEACTION_INITIALIZE) {
			findViewById(R.id.check_sdcard).setOnClickListener(
				new View.OnClickListener() {
					public void onClick(View view) {
						flipToPage(PAGE_MAIN,true);
						prepareSong();
					}
				}
			);
		}
	}
	
	/////////////////////////////////////////////////////// extractor
	// MARKER extractor
	
	private boolean checkExtracted() {
		File cachePath=SongCache.find(m_song.getID());
		if (cachePath==null) {
			return false;
		}
		boolean extracted=AssetExtractor.isExtracted(
			this,
			m_song.getAssetPath(),
			cachePath);
		if (!extracted) {
			MiscHelpers.cleanup(cachePath);
			return false;
		}
		m_song.setFilesPath(cachePath);
		return checkConverted();
	}
	
	private void onExtractorPageAction(int action) {
		switch (action) {
			case PAGEACTION_INITIALIZE:
				findViewById(R.id.extractorPlay).setOnClickListener(
					new OnClickListener() {
						public void onClick(View view) {
							playSong();
						}
					}
				);
				break;
			case PAGEACTION_START:
			{
				showExtractorProgress(0);
				m_handler.postDelayed(m_extractorStarter,CONVERTER_DELAY);
				m_extractorStarting=true;
				break;
			}
			case PAGEACTION_STOP:
			{
				if (m_extractor!=null) {
					m_extractor.stop();
					freeExtractor();
				}
				m_handler.removeCallbacks(m_extractorStarter);
				break;
			}
			case PAGEACTION_PAUSE:
			{
				if (m_extractorStarting) {
					m_handler.removeCallbacks(m_extractorStarter);
					m_extractorStarter.run();
				}
				if (m_extractor!=null) {
					m_extractor.pause();
				}
				break;
			}
			case PAGEACTION_RESUME:
			{
				if (m_extractor!=null) {
					m_extractor.resume();
				}
				break;
			}
		}
	}
	
	private void startExtractor() {
		UIHelpers.flipToChild(this,R.id.extractorFlipper,0,false);
		m_extractor=new Extractor(this,m_song);
		m_extractor.start();
		pollExtractor();
	}
	
	private void pollExtractor() {
		m_extractor.check();
		if (m_extractor.isFinished()) {
			Exception finishError=m_extractor.getFinishError();
			freeExtractor();
			if (finishError!=null) {
				ErrorReportActivity.report(
					this,
					ErrorReportActivity.CAUSE_ERROR,
					"Failed to extract bundled song.",
					null,
					m_song.getErrorDetails(),
					finishError);
				finish();
			} else {
				UIHelpers.flipToChild(this,R.id.extractorFlipper,1,true);
			}
			return;
		}
		showExtractorProgress(m_extractor.getProgress());
		m_handler.postDelayed(m_extractorPoller,100);
	}
	
	private void freeExtractor() {
		m_extractor=null;
		m_handler.removeCallbacks(m_extractorPoller);
	}
	
	private void showExtractorProgress(int progress) {
		String done=UIHelpers.getString(
			this,
			R.string.extracting_song_fmt,
			progress);
		UIHelpers.setText(this,R.id.extractorHead,done);
	}
	
	private static File getExtractedSongFile(SongInfo song) {
		return new File(
			SongCache.getPath(song.getID()),
			SongIni.SONG_FILE);
	}
	private static File getExtractedGuitarFile(SongInfo song) {
		return new File(
			SongCache.getPath(song.getID()),
			SongIni.GUITAR_FILE);
	}
	
	/////////////////////////////////// Extractor
	
	public static class Extractor {
		public Extractor(Context context,SongInfo song) {
			m_context=context;
			m_song=song;
		}
		
		public void start() {
			SongCache.push(m_song.getID());
			m_extractor=new AssetExtractor(
				m_context,
				m_song.getAssetPath(),
				SongCache.getPath(m_song.getID()));
			m_extractor.start();
		}
		public void stop() {
			if (m_finished) {
				return;
			}
			if (m_extractor!=null) {
				m_extractor.stop();
				m_extractor=null;
			}
			if (m_converter!=null) {
				m_converter.stop();
				m_converter=null;
			}
			m_finished=true;
			m_finishError=null;
			SongCache.remove(m_song.getID());
		}
		
		public void pause() {
			if (m_extractor!=null) {
				m_extractor.pause();
			}
			if (m_converter!=null) {
				m_converter.pause();
			}
		}
		public void resume() {
			if (m_extractor!=null) {
				m_extractor.resume();
			}
			if (m_converter!=null) {
				m_converter.resume();
			}
		}
		
		public void check() {
			if (m_finished) {
				return;
			}
			if (m_extractor!=null && m_extractor.isFinished()) {
				m_finishError=m_extractor.getFinishError();
				m_extractor=null;
				if (m_finishError!=null) {
					m_finished=true;
				} else {
					startConverter(true);
				}
				return;
			}
			if (m_converter!=null && m_converter.isFinished()) {
				m_finishError=m_converter.getFinishError();
				m_converter=null;
				if (m_finishError!=null || m_convertingGuitarFile) {
					m_finished=true;
					if (m_finishError==null) {
						setSongFiles();
					} else {
						SongCache.remove(m_song.getID());
					}
				} else {
					startConverter(false);
				}
				return;
			}
		}
		
		public int getProgress() {
			if (m_finished) {
				return 100;
			}
			if (m_extractor!=null) {
				return m_extractor.getProgress()/3;
			}
			if (m_converter!=null) {
				int base=m_convertingGuitarFile?(100*2/3):(100/3);
				return base+m_converter.getProgress()/3;
			}
			return 0;
		}
		
		
		public boolean isFinished() {
			return m_finished;
		}
		public Exception getFinishError() {
			return m_finishError;
		}
		
		///////////////////// implementation
		
		private void startConverter(boolean convertSong) {
			File inputFile=convertSong?
				getExtractedSongFile(m_song):
				getExtractedGuitarFile(m_song);
			File outputFile=convertSong?
				getConvertedSongFile(m_song):
				getConvertedGuitarFile(m_song);
			try {
				m_converter=new Vorbis2RawConverter();
				m_converter.setPriority(CONVERTER_PRIORITY);
				m_converter.start(inputFile,outputFile);
				m_convertingGuitarFile=!convertSong;
			}
			catch (IOException e) {
				m_converter=null;
				m_finished=true;
				m_finishError=e;
			}
		}
		
		private void setSongFiles() {
			m_song.setFilesPath(SongCache.getPath(m_song.getID()));
			m_song.setSongFile(getConvertedSongFile(m_song));
			m_song.setGuitarFile(getConvertedGuitarFile(m_song));
		}
		
		private Context m_context;
		private SongInfo m_song;
		
		private AssetExtractor m_extractor;
		private Vorbis2RawConverter m_converter;
		private boolean m_convertingGuitarFile;
		
		private boolean m_finished;
		private Exception m_finishError;
	}
	
	/////////////////////////////////////////////////////// converter
	// MARKER converter
	
	private boolean checkConverted() {
		File cachePath=SongCache.find(m_song.getID());
		if (cachePath==null) {
			return false;
		}
		File songFile=getConvertedSongFile(m_song);
		File guitarFile=getConvertedGuitarFile(m_song);
		boolean songConverted=checkConverted(
			m_song.getSongFile(),
			songFile);
		boolean guitarConverted=checkConverted(
			m_song.getGuitarFile(),
			guitarFile);
		if (!songConverted || !guitarConverted) {
			return false;
		}
		m_song.setSongFile(songFile);
		m_song.setGuitarFile(guitarFile);
		return true;
	}
	
	private void onConverterPageAction(int action) {
		switch (action) {
			case PAGEACTION_INITIALIZE:
				findViewById(R.id.converterPlay).setOnClickListener(
					new OnClickListener() {
						public void onClick(View view) {
							playSong();
						}
					}
				);
				findViewById(R.id.converterHead).setOnClickListener(
					new OnClickListener() {
						public void onClick(View view) {
							onConverterPageAction(PAGEACTION_STOP);
							playSong();
						}
					}
				);
				break;
			case PAGEACTION_START:
			{
				showConverterProgress(0);
				m_handler.postDelayed(m_converterStarter,CONVERTER_DELAY);
				m_converterStarting=true;
				break;
			}
			case PAGEACTION_STOP:
			{
				if (m_converter!=null) {
					m_converter.stop();
					freeConverter();
				}
				m_handler.removeCallbacks(m_converterStarter);
				break;
			}
			case PAGEACTION_PAUSE:
			{
				if (m_converterStarting) {
					m_handler.removeCallbacks(m_converterStarter);
					m_converterStarter.run();
				}
				if (m_converter!=null) {
					m_converter.pause();
				}
				break;
			}
			case PAGEACTION_RESUME:
			{
				if (m_converter!=null) {
					m_converter.resume();
				}
				break;
			}
		}
	}
	
	private void startConverter() {
		UIHelpers.flipToChild(this,R.id.converterFlipper,0,false);
		m_converter=new Converter(m_song);
		m_converter.start();
		pollConverter();
	}
	
	private void pollConverter() {
		m_converter.check();
		if (m_converter.isFinished()) {
			Exception finishError=m_converter.getFinishError();
			freeConverter();
			if (finishError!=null) {
				ErrorReportActivity.report(
					this,
					ErrorReportActivity.CAUSE_ERROR,
					"Failed to decode song.",
					null,
					m_song.getErrorDetails(),
					finishError);
				finish();
			} else {
				UIHelpers.flipToChild(this,R.id.converterFlipper,1,true);
			}
			return;
		}
		showConverterProgress(m_converter.getProgress());
		m_handler.postDelayed(m_converterPoller,100);
	}
	
	private void freeConverter() {
		m_converter=null;
		m_handler.removeCallbacks(m_converterPoller);
	}
	
	private void showConverterProgress(int progress) {
		String done=UIHelpers.getString(
			this,
			R.string.converting_song_fmt,
			progress);
		UIHelpers.setText(this,R.id.converterHead,done);
	}
	
	private static boolean checkConverted(File file,File convertedFile) {
		if (!file.exists()) {
			return true;
		}
		if (!convertedFile.exists()) {
			return false;
		}
		return Vorbis2RawConverter.isConvertedFile(file,convertedFile);
	}
	
	private static File getConvertedSongFile(SongInfo song) {
		return new File(
			SongCache.getPath(song.getID()),
			SongPlayer.getRawFileName(SongIni.SONG_FILE));
	}
	private static File getConvertedGuitarFile(SongInfo song) {
		return new File(
			SongCache.getPath(song.getID()),
			SongPlayer.getRawFileName(SongIni.GUITAR_FILE));
	}
	
	/////////////////////////////////// Converter
	
	private static class Converter {
		public Converter(SongInfo song) {
			m_song=song;
		}
		
		public void start() {
			SongCache.push(m_song.getID());
			m_haveSongFile=m_song.getSongFile().exists();
			m_haveGuitarFile=m_song.getGuitarFile().exists();
			if (!m_haveSongFile && !m_haveGuitarFile) {
				m_finished=true;
				return;
			}
			startConverter(m_haveSongFile);
		}
		public void stop() {
			if (m_finished) {
				return;
			}
			if (m_converter!=null) {
				m_converter.stop();
				m_converter=null;
			}
			m_finished=false;
			m_finishError=null;
			SongCache.remove(m_song.getID());
		}
		
		public void pause() {
			if (m_converter!=null) {
				m_converter.pause();
			}
		}
		public void resume() {
			if (m_converter!=null) {
				m_converter.resume();
			}
		}
		
		public void check() {
			if (m_finished) {
				return;
			}
			if (m_converter.isFinished()) {
				m_finishError=m_converter.getFinishError();
				m_converter=null;
				if (m_finishError!=null ||
					m_convertingGuitarFile==m_haveGuitarFile)
				{
					m_finished=true;
					if (m_finishError==null) {
						setSongFiles();
					} else {
						SongCache.remove(m_song.getID());
					}
				} else {
					startConverter(m_convertingGuitarFile);
				}
			}
		}
		
		public int getProgress() {
			if (m_finished) {
				return 100;
			}
			if (m_converter==null) {
				return 0;
			}
			if (!m_haveSongFile || !m_haveGuitarFile) {
				return m_converter.getProgress();
			} else {
				int base=m_convertingGuitarFile?(100/2):0;
				return base+m_converter.getProgress()/2;
			}
		}
		
		public boolean isFinished() {
			return m_finished;
		}
		public Exception getFinishError() {
			return m_finishError;
		}
		
		///////////////////// implementation
		
		private void startConverter(boolean convertSongFile) {
			File inputFile=convertSongFile?
				m_song.getSongFile():
				m_song.getGuitarFile();
			File outputFile=convertSongFile?
				getConvertedSongFile(m_song):
				getConvertedGuitarFile(m_song);
			try {
				m_converter=new Vorbis2RawConverter();
				m_converter.setPriority(CONVERTER_PRIORITY);
				m_converter.start(inputFile,outputFile);
				m_convertingGuitarFile=!convertSongFile;
			}
			catch (IOException e) {
				m_finished=true;
				m_finishError=e;
			}
		}
		
		private void setSongFiles() {
			if (m_haveSongFile) {
				m_song.setSongFile(getConvertedSongFile(m_song));
			}
			if (m_haveGuitarFile) {
				m_song.setGuitarFile(getConvertedGuitarFile(m_song));
			}
		}
		
		private SongInfo m_song;
		private boolean m_haveSongFile;
		private boolean m_haveGuitarFile;
		
		private Vorbis2RawConverter m_converter;
		private boolean m_convertingGuitarFile;
		
		private boolean m_finished;
		private Exception m_finishError;
	}

	/////////////////////////////////////////////////////// skills
	
	private void initializeSkillViews() {
		for (int i=0;i!=Song.SKILL_COUNT;++i) {
			int id=SKILLPAGE_IDS[i*2];
			getSkillView(id).setCallback(this);
		}
	}
	
	private void setupSkillViews() {
		SongDB.Record songRecord=SongDB.find(m_song.getID());
		for (int i=Song.SKILL_COUNT-1;i!=-1;--i) {
			SongDB.Score score=null;
			if (songRecord!=null) {
				score=songRecord.getScore(Song.indexToSkill(i));
			}
			setupSkillView(i,score,SKILLPAGE_IDS[i*2],SKILLPAGE_IDS[i*2+1]);
		}
	}

	private void setupSkillView(int skillIndex,SongDB.Score score,int viewID,int dividerID) {
		int skill=Song.indexToSkill(skillIndex);
		if ((m_song.getSkills() & skill)!=0) {
			UIHelpers.setViewVisibility(this,dividerID,View.VISIBLE);
			PlayableSkillView skillView=getSkillView(viewID);
			skillView.setVisibility(View.VISIBLE);
			skillView.setup(skill,score);
		} else {
			UIHelpers.setViewVisibility(this,dividerID,View.GONE);
			UIHelpers.setViewVisibility(this,viewID,View.GONE);
		}
	}

	private PlayableSkillView getSkillView(int id) {
		return (PlayableSkillView)findViewById(id);
	}
	
	private void animateSkillViews(int offset) {
		int delay=UIHelpers.getInteger(
			this,
			R.integer.anim_body_delay);
		for (int i=Song.SKILL_COUNT-1;i!=-1;--i) {
			int skill=Song.indexToSkill(i);
			if ((m_song.getSkills() & skill)==0) {
				continue;
			}
			int viewID=SKILLPAGE_IDS[i*2];
			int dividerID=SKILLPAGE_IDS[i*2+1];
			UIHelpers.startViewAnimation(
				this,
				dividerID,R.anim.button_in,
				offset);
			UIHelpers.startViewAnimation(
				this,
				viewID,R.anim.button_in,
				offset);
			offset+=delay;
		}
		UIHelpers.startViewAnimation(
			this,
			R.id.lastDivider,R.anim.button_in,
			offset);
	}
	
	/////////////////////////////////////////////////////// pages
	
	protected void doPageAction(int page,int action) {
		switch (page) {
			case PAGE_SDCARD:
				onSDCardPageAction(action);
				break;
			case PAGE_EXTRACTOR:
				onExtractorPageAction(action);
				break;
			case PAGE_CONVERTER:
				onConverterPageAction(action);
				break;
		}
	}
	
	///////////////////////////////////////////// data
	
	private SongInfo m_song;
	private SongInfo m_originalSong;
	
	private Handler m_handler=new Handler();
	
	private Extractor m_extractor;
	private boolean m_extractorStarting;
	private Runnable m_extractorPoller=new Runnable() {
		public void run() {
			pollExtractor();
		}
	};
	private Runnable m_extractorStarter=new Runnable() {
		public void run() {
			m_extractorStarting=false;
			startExtractor();
		}
	};

	private Converter m_converter;
	private boolean m_converterStarting;
	private Runnable m_converterPoller=new Runnable() {
		public void run() {
			pollConverter();
		}
	};
	private Runnable m_converterStarter=new Runnable() {
		public void run() {
			m_converterStarting=false;
			startConverter();
		}
	};
	
	/////////////////////////////////// constants
	
	private static final int[] SKILLPAGE_IDS=new int[]{
		R.id.amazing,R.id.amazingDivider,
		R.id.medium,R.id.mediumDivider,
		R.id.easy,R.id.easyDivider,
		R.id.supaeasy,R.id.supaeasyDivider,
	};
	
	private static final int 
		CONVERTER_PRIORITY		=Process.THREAD_PRIORITY_DEFAULT,
		CONVERTER_DELAY			=700;
	
	private static final int 
		PAGE_SDCARD				=1,
		PAGE_EXTRACTOR			=2,
		PAGE_CONVERTER			=3;
}
