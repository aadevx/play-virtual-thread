package models;

import play.db.jdbc.BaseTable;
import play.db.jdbc.Id;
import play.db.jdbc.Table;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Table(name = "BERITA")
public class Berita extends BaseTable {
    @Id(sequence = "seq_berita", function = "nextsequence")
    public Long brt_id;

    public String brt_judul;

    public String brt_isi;

    public Date brt_tanggal;

    public Long brt_id_attachment;

    public Date brt_expires;

    //relasi ke Sub_tag
    public String stg_id;

    //relasi ke Pegawai
    public Long peg_id;

    public static void simpan() {
//        Berita berita = new Berita();
//        berita.brt_judul = "Berita testing jdbc";
//        berita.brt_isi = "Berita testing jdbc";
//        berita.brt_tanggal = new Date();
//        berita.stg_id = "LELANG";
//        berita.peg_id = 2411999L;
//        berita.save();
//        Berita berita1 = Berita.findById(berita.brt_id);
//        berita1.brt_judul = "update berita testing jdbc";
//        berita1.save();
        List<Berita> list = new ArrayList<>();
        for(int i = 0 ; i< 5 ;i++) {
            Berita berita = new Berita();
            berita.brt_judul = "Berita testing jdbc";
            berita.brt_isi = "Berita testing jdbc";
            berita.brt_tanggal = new Date();
            berita.stg_id = "LELANG";
            berita.peg_id = 2411999L;
            list.add(berita);
        }
        Berita.saveAll(list);
    }

}
