package models;

import play.db.jdbc.BaseTable;
import play.db.jdbc.Enumerated;
import play.db.jdbc.Id;
import play.db.jdbc.Table;

import javax.persistence.EnumType;
import java.util.Date;


/**
 * @author development
 *
 */
@Table(name="SUB_TAG")
public class Sub_tag extends BaseTable {
	
	@Enumerated(EnumType.STRING)
	public enum JenisSubtag {
		BERITA("Berita"), LOWONGAN("Lowongan Pekerjaan"), LELANG("Pengumuman Tender"),
		SPECIAL_CONTENT("Konten Khusus"), REGULASI("Regulasi"), SYSTEM_MESSAGE("Pesan Sistem"), 
		MARQUEE("Pesan Berjalan"), FORUM_PENGADAAN("Forum Pengadaan"), PENGUMUMAN("Pengumuman Massal"),

		LPSE_NASIONAL("LPSE Nasional"), LPSE_REGIONAL("LPSE Regional"), LPSE_DEPARTEMEN("LPSE Kementerian"),

		KONTES("Kontes"), PELELANGAN_SEDERHANA("Pelelangan Sederhana"), PELELANGAN_TERBATAS("Pelelangan Terbatas"), 
		PELELANGAN_UMUM("Pelelangan Umum"), PEMILIHAN_LANGSUNG("Pemilihan Langsung"), SAYEMBARA("Sayembara"), 
		SELEKSI_PEMILIHAN("Seleksi Pemilihan"), SELEKSI_UMUM("Seleksi Umum"),

		IJIN_USAHA("Izin Usaha"), PAJAK("Pajak"), AKTA("Akta"),

		SURAT_PERJANJIAN("Surat Perjanjian"), SSKK("SSKK"), BAP("BAP"), SPPBJ("SPPBJ"), SPMK("SPMK"),SURAT_TUGAS("Surat Tugas"),

		PL("Pengumuman Pengadaan/Penunjukan Langsung");
		
		public final String label;
		
		private JenisSubtag(String label) {
			this.label = label;
		}
		
		public boolean isAttachment() {
			return (this == SPECIAL_CONTENT) || (this == BERITA)
					|| (this == FORUM_PENGADAAN) || (this == LOWONGAN)
					|| (this == REGULASI) || (this == LELANG);
		}

		public boolean isExpire() {
			return (this == MARQUEE) || (this == SYSTEM_MESSAGE)
					|| (this == LOWONGAN) || (this == FORUM_PENGADAAN)
					|| (this == PENGUMUMAN);
		}
		
		public boolean isMandatoryExpiredDate(){
			return (this == MARQUEE) || (this == BERITA)
					|| (this == REGULASI);
		}

		public boolean isForum() {
			return this == FORUM_PENGADAAN;
		}

		public boolean isSpecialContent() {
			return this == SPECIAL_CONTENT;
		}

		public boolean isBerita() {
			return this == BERITA;
		}
		
		public String lowerCase() {
			return toString().toLowerCase();
		}
	}

	@Id
	public String stg_id;

	public String stg_nama;
	
	public String tag_id;

	public String audituser = "ADMIN";

//	public String audittype="C";

//	public Date auditupdate = new Date();

	public static void simpan() {
		Sub_tag sub = new Sub_tag();
		sub.stg_id="NEWTAG";
		sub.tag_id="NEWTAG";
		sub.stg_nama="NEWNAMA";
		sub.save();
	}

}
