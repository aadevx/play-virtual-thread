package models;

import java.util.Date;

import play.data.validation.Email;
import play.data.validation.Match;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Phone;
import play.data.validation.Required;
import play.data.validation.URL;
import play.db.jdbc.BaseTable;
import play.db.jdbc.Id;
import play.db.jdbc.Table;


@Table(name="REKANAN")
public class Rekanan extends BaseTable {
	
	public static int REKANAN_BARU = 0;

	public static int REKANAN_DISETUJUI = 1;

	public static int REKANAN_DITOLAK = -2;

	public static int REKANAN_EXPIRED = -3;
	
	public static final int MIGRASI_SIKAP_BELUM = 0;
	public static final int MIGRASI_SIKAP_PROSES = 1;
	public static final int MIGRASI_SIKAP_SELESAI = 2;
	
	public static String PUSAT = "P";
    public static String CABANG = "C";

	@Id(sequence="seq_rekanan", function="nextsequence")	
	public Long rkn_id;
	
    @Required
	public String rkn_nama;
    @Required
	public String rkn_alamat;
	@Match(message = "rekanan.rkn_kodepos", value = "[0-9]{5}")
	public String rkn_kodepos;
    @Required
    @Phone
	public String rkn_telepon;
    @Phone
	public String rkn_fax;
    @Phone
	public String rkn_mobile_phone;
    
    @Required
    @MaxSize(20)
	public String rkn_npwp;

	public String rkn_pkp;

	public String rkn_statcabang;
	
	@Required
	@Email
	public String rkn_email;
	
	@URL
	public String rkn_website;

	public Date rkn_tgl_daftar;

	public Date rkn_tgl_setuju;

	public String rkn_almtpusat;
	@Phone
	public String rkn_telppusat;
	@Phone
	public String rkn_faxpusat;
	@Email
	public String rkn_emailpusat;
	
	@Required
	public String rkn_namauser;

	public Integer rkn_isactive;

	public Integer rkn_status = 0;

	@MaxSize(200)
	public String rkn_keterangan;

	public String rkn_status_verifikasi;

	public String ver_namauser;
	//relasi ke Bentuk_usaha
	public String btu_id;
	//relasi ke Kabupaten
	public Long kbp_id;
	//relasi ke Ppe_site
	public String pps_id;
	//relasi ke Repository
	public Long repo_id;
	// OSD
	public Long cer_id;
	// integrasi dengan sikap
	public Integer status_migrasi = MIGRASI_SIKAP_BELUM;
	public Date last_sync_vms;
	
	@Required
	@MinSize(6)
	public String passw;

	public Integer isbuiltin = 0;

	public Integer disableuser;
	
}
