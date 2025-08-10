package models;

import play.db.jdbc.BaseTable;
import play.db.jdbc.Id;
import play.db.jdbc.Table;

import java.util.Date;

@Table(name = "USRSESSION")
public class Usrsession extends BaseTable {
    @Id(sequence = "seq_epns", function = "nextsequence")
    public Long sessionid;

    public String station;

    public Date sessiontime;

    public Date logouttime;

    public String osname;

    public Subsystem systemid;

    public String userid;

    public Integer jenis; // 0 : pegawai , 1 : rekanan

    /**
     * Fungsi {@code setWaktuLogin} digunakan untuk set data waktu login
     * pengguna pada tabel usrsession
     *
     * @param userid     user id pengguna
     * @param ip_address ip address pengguna
     */
    public static Usrsession setWaktuLogin(String userid,  String ip_address, boolean rekanan) {
        Usrsession usrsession = new Usrsession();
        usrsession.userid = userid; // id uppercase
        usrsession.systemid = Subsystem.EPNS;
        usrsession.sessiontime = new Date();
        usrsession.station = ip_address;
        usrsession.jenis = rekanan ? 1 : 0;
        usrsession.save();
        return usrsession;
    }

    public Long getSessionid() {
        return sessionid;
    }

    public void setSessionid(Long sessionid) {
        this.sessionid = sessionid;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public Date getSessiontime() {
        return sessiontime;
    }

    public void setSessiontime(Date sessiontime) {
        this.sessiontime = sessiontime;
    }

    public Date getLogouttime() {
        return logouttime;
    }

    public void setLogouttime(Date logouttime) {
        this.logouttime = logouttime;
    }

    public String getOsname() {
        return osname;
    }

    public void setOsname(String osname) {
        this.osname = osname;
    }

    public Subsystem getSystemid() {
        return systemid;
    }

    public void setSystemid(Subsystem systemid) {
        this.systemid = systemid;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public Integer getJenis() {
        return jenis;
    }

    public void setJenis(Integer jenis) {
        this.jenis = jenis;
    }
}
