package com.rapportangi.backend.service;

import com.rapportangi.backend.dto.ReportResponseDto;
import java.awt.Color;
import java.io.*;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ReportPdfService {

  public byte[] render(ReportResponseDto report) throws IOException {
    try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
      var layout = new Layout(doc, report);
      layout.startPage();
      layout.text("RAPPORT D'ACTIVITÉ COMMERCIALE", 16, new Color(89, 22, 129));
      layout.space(12);
      layout.text(report.getTitle(), 14, Color.BLACK);
      layout.space(10);
      layout.text("Commercial : " + report.getAuthorName(), 11, Color.BLACK);
      layout.text(
        "Équipe : " +
          (report.getTeam() == null
            ? "Non affectée (ancien rapport)"
            : report.getTeam()),
        11,
        Color.BLACK
      );
      layout.text(
        "Journée : " +
          report
            .getReportDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
        11,
        Color.BLACK
      );
      if (report.getZone() != null) layout.text(
        "Zone : " + report.getZone(),
        11,
        Color.BLACK
      );
      layout.text("Statut : " + status(report.getStatus()), 11, Color.BLACK);
      layout.text(
        "Code de parrainage : " +
          (report.getReferralCode() == null
            ? "Non renseigné"
            : report.getReferralCode()),
        11,
        Color.BLACK
      );
      layout.text(
        "Mode : " +
          ("STATS".equals(report.getMode())
            ? "Statistiques et observations"
            : "Compte rendu écrit"),
        11,
        Color.BLACK
      );
      layout.space(16);
      if ("STATS".equals(report.getMode())) {
        layout.text("BILAN CHIFFRÉ", 12, new Color(89, 22, 129));
        layout.text(
          "Prospects contactés : " + report.getProspectsContacted(),
          12,
          Color.BLACK
        );
        layout.text(
          "Comptes inscrits (uniques) : " + report.getRegistrations(),
          12,
          Color.BLACK
        );
        layout.text(
          "APP_API".equals(report.getStatsSource())
            ? "Source : API mobile de l'équipe (instantané enregistré)."
            : "Source : chiffres déclarés par le commercial, non vérifiés par une API.",
          10,
          new Color(91, 91, 105)
        );
        if (report.getStatsFetchedAt() != null) layout.text(
          "Importé le " +
            report
              .getStatsFetchedAt()
              .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
          10,
          new Color(91, 91, 105)
        );
        layout.space(16);
      }
      if ("STATS".equals(report.getMode()) && "AZO".equals(report.getTeam())) {
        layout.text(
          "PROFILS AZO ENREGISTRÉS / ACTIVÉS",
          12,
          new Color(89, 22, 129)
        );
        if (
          report.getProfileCounts() == null ||
          report.getProfileCounts().isEmpty()
        ) layout.text("Répartition non fournie.", 10, Color.DARK_GRAY);
        else for (var profile : AzoProfiles.ALL) {
          if (
            report.getProfileCounts().containsKey(profile.code())
          ) layout.text(
            profile.label() +
              " : " +
              report.getProfileCounts().get(profile.code()),
            11,
            Color.BLACK
          );
        }
        layout.text(
          "Un compte peut activer plusieurs profils : leur somme n'est pas le nombre de comptes uniques.",
          9,
          Color.DARK_GRAY
        );
        layout.space(16);
      }
      if ("AZO".equals(report.getTeam())) {
        layout.text("PERSONNES INSCRITES SUR AZO", 12, new Color(89, 22, 129));
        if (report.getAzoRegistrations().isEmpty()) layout.text(
          "Liste nominative non fournie.",
          10,
          Color.DARK_GRAY
        );
        else {
          layout.text(
            "Liste déclarée par le commercial : nom, prénom et profil d'inscription.",
            9,
            Color.DARK_GRAY
          );
          int row = 0;
          for (var entry : report.getAzoRegistrations()) {
            layout.text(
              ++row +
                ". Nom : " +
                entry.lastName() +
                " | Prénom : " +
                entry.firstName(),
              11,
              Color.BLACK
            );
            layout.text(
              "Profil : " + AzoProfiles.label(entry.profileCode()),
              10,
              Color.DARK_GRAY
            );
            layout.space(5);
          }
        }
        layout.space(16);
      }
      if (!report.getGoalCounts().isEmpty()) {
        layout.text(
          "RÉALISATIONS DU JOUR POUR LES OBJECTIFS",
          12,
          new Color(89, 22, 129)
        );
        for (var goal : SalesGoals.definitions(report.getTeam()))
          if (report.getGoalCounts().containsKey(goal.code())) layout.text(
            goal.label() + " : " + report.getGoalCounts().get(goal.code()),
            11,
            Color.BLACK
          );
        layout.text(
          "Comptées dans le bilan mensuel après validation du superviseur.",
          9,
          Color.DARK_GRAY
        );
        layout.space(16);
      }
      layout.text(
        "STATS".equals(report.getMode())
          ? "OBSERVATIONS"
          : "DÉROULEMENT DE LA JOURNÉE",
        12,
        new Color(89, 22, 129)
      );
      layout.space(4);
      layout.text(
        report.getContent().isBlank()
          ? "Aucune observation ajoutée."
          : report.getContent(),
        11,
        Color.BLACK
      );
      layout.space(16);
      if (report.getReviews() != null && !report.getReviews().isEmpty()) {
        layout.text("SUIVI ET CONFIRMATION", 12, new Color(89, 22, 129));
        for (var review : report.getReviews()) {
          layout.text(
            status(review.action()) +
              " - " +
              review.reviewerName() +
              " - " +
              review
                .createdAt()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
            10,
            new Color(91, 91, 105)
          );
          layout.text(
            review.comment().isBlank()
              ? "Confirmation du rapport."
              : review.comment(),
            11,
            Color.BLACK
          );
          layout.space(10);
        }
      }
      layout.finishPage();
      doc.getDocumentInformation().setTitle(report.getTitle());
      doc.getDocumentInformation().setAuthor(report.getAuthorName());
      doc.save(out);
      return out.toByteArray();
    }
  }

  public static String status(String value) {
    return switch (value) {
      case "DRAFT" -> "Brouillon";
      case "SUBMITTED" -> "Soumis";
      case "RETURNED" -> "À confirmer / corriger";
      case "CONFIRMED" -> "Confirmé";
      default -> value;
    };
  }

  private static class Layout {

    final PDDocument doc;
    final ReportResponseDto report;
    final PDType0Font font;
    PDPageContentStream stream;
    float y;
    int pageNumber = 0;
    final java.util.Map<String, PDImageXObject> logos =
      new java.util.HashMap<>();

    Layout(PDDocument doc, ReportResponseDto report) throws IOException {
      this.doc = doc;
      this.report = report;
      try (
        var in = new ClassPathResource(
          "branding/DejaVuSans.ttf"
        ).getInputStream()
      ) {
        font = PDType0Font.load(doc, in);
      }
    }

    void startPage() throws IOException {
      var page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      stream = new PDPageContentStream(doc, page);
      pageNumber++;
      logo("angi.jpeg", 48, 748, 66, 66, 0, 0, 1, 1);
      draw("ANGI DSN PRO SARL", 128, 788, 13, new Color(89, 22, 129));
      draw(
        "Direction commerciale - rapports terrain",
        128,
        768,
        9,
        new Color(91, 91, 105)
      );
      if ("AZO".equals(report.getTeam())) logo(
        "azo.png",
        477,
        748,
        66,
        66,
        .18f,
        .23f,
        .62f,
        .55f
      );
      if ("ZEHAMI".equals(report.getTeam())) logo(
        "zehami.jpeg",
        477,
        748,
        66,
        66,
        .32f,
        .08f,
        .36f,
        .36f
      );
      if (report.getTeam() != null) draw(
        report.getTeam(),
        480,
        733,
        9,
        new Color(89, 22, 129)
      );
      stream.setStrokingColor(new Color(235, 114, 39));
      stream.moveTo(48, 722);
      stream.lineTo(547, 722);
      stream.stroke();
      y = 696;
    }

    void logo(
      String name,
      float x,
      float y,
      float w,
      float h,
      float sx,
      float sy,
      float sw,
      float sh
    ) throws IOException {
      var image = logos.get(name);
      if (image == null) {
        byte[] data;
        try (
          var in = new ClassPathResource("branding/" + name).getInputStream()
        ) {
          data = in.readAllBytes();
        }
        image = PDImageXObject.createFromByteArray(doc, data, name);
        logos.put(name, image);
      }
      float cropW = image.getWidth() * sw,
        cropH = image.getHeight() * sh,
        scale = Math.min(w / cropW, h / cropH);
      float boxW = cropW * scale,
        boxH = cropH * scale,
        bx = x + (w - boxW) / 2,
        by = y + (h - boxH) / 2;
      // Placement par fenêtre : le fichier original du logo est conservé, sans retouche.
      stream.saveGraphicsState();
      stream.addRect(bx, by, boxW, boxH);
      stream.clip();
      stream.drawImage(
        image,
        bx - image.getWidth() * sx * scale,
        by + boxH - (image.getHeight() - image.getHeight() * sy) * scale,
        image.getWidth() * scale,
        image.getHeight() * scale
      );
      stream.restoreGraphicsState();
    }

    String safe(String value) {
      var result = new StringBuilder();
      value.codePoints().forEach(cp -> {
        String c = new String(Character.toChars(cp));
        try {
          font.encode(c);
          result.append(c);
        } catch (Exception e) {
          result.append('?');
        }
      });
      return result.toString();
    }

    void draw(String value, float x, float baseline, float size, Color color)
      throws IOException {
      stream.beginText();
      stream.setFont(font, size);
      stream.setNonStrokingColor(color);
      stream.newLineAtOffset(x, baseline);
      stream.showText(safe(value));
      stream.endText();
    }

    void text(String value, float size, Color color) throws IOException {
      for (String paragraph : value.replace("\t", "    ").split("\\R", -1)) {
        String clean = safe(paragraph);
        var line = new StringBuilder();
        // Wrap by Unicode codepoint, including very long words, at a measured font width.
        for (int cp : clean.codePoints().toArray()) {
          String c = new String(Character.toChars(cp));
          if (
            (font.getStringWidth(line.toString() + c) / 1000) * size > 499 &&
            line.length() > 0
          ) {
            int split = line.lastIndexOf(" ");
            if (split > 0) {
              String remainder = line.substring(split + 1);
              line(line.substring(0, split), size, color);
              line.setLength(0);
              line.append(remainder);
            } else {
              line(line.toString(), size, color);
              line.setLength(0);
            }
          }
          line.append(c);
        }
        line(line.toString(), size, color);
      }
    }

    void line(String text, float size, Color color) throws IOException {
      if (y < 68) {
        finishPage();
        startPage();
      }
      draw(text, 48, y, size, color);
      y -= size + 5;
    }

    void space(float n) {
      y -= n;
    }

    void finishPage() throws IOException {
      draw(
        "ANGI DSN PRO SARL | Rapport #" +
          report.getId() +
          " | Page " +
          pageNumber,
        48,
        35,
        8,
        new Color(91, 91, 105)
      );
      stream.close();
    }
  }
}
