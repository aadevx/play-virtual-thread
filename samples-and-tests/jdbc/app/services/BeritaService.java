package services;

import models.Berita;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Singleton
public class BeritaService {

    public void saveListBerita() {
        List<Berita> list = new ArrayList<>();
        for(int i = 0 ; i< 5 ;i++) {
            Berita berita = new Berita();
            berita.brt_judul = "Berita testing jdbc";
            berita.brt_isi = "Berita testing jdbc";
            berita.brt_tanggal = new Date();
            berita.stg_id = "LELANG";
            berita.peg_id = 2411999L;
            berita.save();
            list.add(berita);
        }
        Berita.saveAll(list);
    }

    public void saveBerita() {
        Berita berita = new Berita();
        berita.brt_judul = "Berita testing jdbc";
        berita.brt_isi = "Berita testing jdbc";
        berita.brt_tanggal = new Date();
        berita.stg_id = "LELANG";
        berita.peg_id = 2411999L;
        berita.save();
    }
}
