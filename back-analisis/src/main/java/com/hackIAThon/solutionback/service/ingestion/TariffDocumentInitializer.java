package com.hackIAThon.solutionback.service.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Indexa tarifarios predefinidos para múltiples ramos de seguros al arrancar.
 * Genera PDFs en memoria con PDFBox y los pasa a TariffIngestionService.
 * Idempotente: cada tariff se indexa una sola vez (dedup por nombre de archivo).
 */
@Component
@Order(2)
public class TariffDocumentInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TariffDocumentInitializer.class);

    private final TariffIngestionService ingestionService;

    public TariffDocumentInitializer(TariffIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    record TariffDef(String filename, String title, String subtitle, List<String> lines) {}

    @Override
    public void run(String... args) {
        List<TariffDef> tariffs = List.of(buildAutomotriz(), buildSalud(), buildHogar(), buildVida(), buildGeneral());
        for (TariffDef t : tariffs) {
            try {
                byte[] pdfBytes = createPdf(t);
                int chunks = ingestionService.ingestPdf(t.filename(), pdfBytes);
                if (chunks > 0) {
                    log.info("Built-in tariff indexed: '{}' ({} chunks)", t.filename(), chunks);
                }
            } catch (Exception e) {
                log.error("Failed to index built-in tariff '{}': {}", t.filename(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────── TARIFARIOS ────────────────────────────────────

    private TariffDef buildAutomotriz() {
        return new TariffDef(
            "tarifario_automotriz_repuestos_servicios.pdf",
            "TARIFARIO — REPUESTOS Y SERVICIOS AUTOMOTRICES",
            "Aplicable a: Pólizas de Vehículos, Siniestros Automotrices | Versión 2025-01",
            List.of(
                "== CARROCERÍA Y EXTERIORES ==",
                "Parachoques frontal completo sedan: $750.00",
                "Parachoques trasero completo sedan: $680.00",
                "Reemplazo parachoques frontal: $800.00",
                "Reemplazo parachoques trasero: $720.00",
                "Faro delantero izquierdo completo: $390.00",
                "Faro delantero derecho completo: $390.00",
                "Faro trasero izquierdo completo: $260.00",
                "Faro trasero derecho completo: $260.00",
                "Espejo retrovisor lateral izquierdo: $120.00",
                "Espejo retrovisor lateral derecho: $120.00",
                "Capó completo: $480.00",
                "Maletero tapa trasera: $420.00",
                "Puerta delantera: $380.00",
                "Puerta trasera: $340.00",
                "Guardafango delantero: $220.00",
                "Guardafango trasero: $200.00",
                "",
                "== CRISTALES Y VIDRIOS ==",
                "Parabrisas delantero (instalado): $380.00",
                "Parabrisas trasero (instalado): $280.00",
                "Vidrio ventana lateral (instalado): $150.00",
                "Cristal luna puerta delantera: $160.00",
                "",
                "== PINTURA Y LATONERÍA ==",
                "Pintura completa vehículo sedán: $1200.00",
                "Pintura capó: $180.00",
                "Pintura puerta: $150.00",
                "Pintura guardafango: $130.00",
                "Pintura parachoques: $120.00",
                "Pintura maletero: $160.00",
                "Pintura capó mano de obra hora: $45.00",
                "Latonería y enderezado puerta: $95.00",
                "Latonería capó enderezado: $110.00",
                "",
                "== MOTOR Y MECÁNICA ==",
                "Cambio de aceite y filtro servicio: $45.00",
                "Aceite motor sintético por litro: $14.00",
                "Filtro de aceite: $18.00",
                "Filtro de aire: $28.00",
                "Filtro de combustible: $32.00",
                "Bujías juego 4 piezas: $55.00",
                "Correa de distribución kit: $180.00",
                "Banda de accesorios: $65.00",
                "Radiador (reemplazo): $350.00",
                "Batería estándar 45Ah: $120.00",
                "Batería premium 60Ah: $180.00",
                "Alternador reemplazo: $280.00",
                "Motor de arranque reemplazo: $220.00",
                "",
                "== FRENOS ==",
                "Pastillas de freno delanteras juego: $85.00",
                "Pastillas de freno traseras juego: $75.00",
                "Disco de freno delantero por pieza: $120.00",
                "Disco de freno trasero por pieza: $100.00",
                "Tambor de freno: $95.00",
                "Liquido de frenos DOT 4 litro: $22.00",
                "Liquido de frenos DOT 4 2 litros: $38.00",
                "Cambio liquido de frenos servicio: $40.00",
                "Calibrador de freno delantero: $150.00",
                "",
                "== SUSPENSIÓN Y DIRECCIÓN ==",
                "Amortiguador delantero por pieza: $185.00",
                "Amortiguador trasero por pieza: $155.00",
                "Par amortiguadores delanteros: $360.00",
                "Par amortiguadores traseros: $300.00",
                "Rotula de dirección: $75.00",
                "Terminal de dirección: $55.00",
                "Barra estabilizadora buje: $35.00",
                "Alineación y balanceo servicio: $50.00",
                "Alineacion balanceo cuatro ruedas: $55.00",
                "Balanceo ruedas servicio: $30.00",
                "",
                "== NEUMÁTICOS ==",
                "Neumático 195/65R15 (por pieza): $95.00",
                "Neumático 205/55R16 (por pieza): $110.00",
                "Neumático 225/45R17 (por pieza): $135.00",
                "Montaje y balanceo por neumático: $12.00",
                "",
                "== AIRE ACONDICIONADO ==",
                "Recarga gas refrigerante R134a: $65.00",
                "Filtro habitáculo aire acondicionado: $25.00",
                "Compresor aire acondicionado: $420.00",
                "",
                "== ACCESORIOS Y VARIOS ==",
                "Kit escobillas limpiaparabrisas: $38.00",
                "Escobilla limpiaparabrisas unidad: $20.00",
                "Liquido limpiaparabrisas 1 litro: $8.00",
                "Antena vehiculo: $25.00",
                "Diagnóstico electrónico OBD: $35.00",
                "Revisión general mecánica: $55.00",
                "Mano de obra mecánica por hora: $40.00",
                "Mano de obra especializada hora: $55.00"
            )
        );
    }

    private TariffDef buildSalud() {
        return new TariffDef(
            "tarifario_siniestros_salud.pdf",
            "TARIFARIO — SEGUROS DE SALUD Y GASTOS MÉDICOS",
            "Aplicable a: Pólizas de Salud, Accidentes Personales, Cirugía | Versión 2025-01",
            List.of(
                "== CONSULTAS MÉDICAS ==",
                "Consulta médica general: $45.00",
                "Consulta médico especialista: $85.00",
                "Consulta médico sub-especialista: $110.00",
                "Teleconsulta médica: $30.00",
                "Consulta urgencias no hospitalaria: $75.00",
                "",
                "== DIAGNÓSTICO POR IMAGEN ==",
                "Radiografía simple 1 posición: $40.00",
                "Radiografía simple 2 posiciones: $60.00",
                "Ecografía abdominal: $90.00",
                "Ecografía obstétrica: $95.00",
                "Tomografía simple 1 región: $280.00",
                "Tomografía con contraste 1 región: $350.00",
                "Resonancia magnética simple: $480.00",
                "Resonancia magnética con contraste: $580.00",
                "Mamografía: $80.00",
                "Densitometría ósea: $120.00",
                "",
                "== LABORATORIO CLÍNICO ==",
                "Hemograma completo: $18.00",
                "Química sanguínea básica glucosa urea creatinina: $25.00",
                "Perfil lipídico colesterol triglicéridos: $35.00",
                "Perfil hepático: $40.00",
                "Examen general de orina: $15.00",
                "Prueba de embarazo: $20.00",
                "Cultivo y antibiograma: $55.00",
                "Prueba PCR infección viral: $65.00",
                "",
                "== HOSPITALIZACIÓN ==",
                "Habitación individual por día: $450.00",
                "Habitación compartida por día: $280.00",
                "Unidad de cuidados intensivos UCI por día: $1200.00",
                "Unidad de cuidados intermedios por día: $750.00",
                "Sala de recuperación hasta 6 horas: $220.00",
                "",
                "== CIRUGÍA ==",
                "Cirugía menor ambulatoria: $600.00",
                "Cirugía mayor electiva sin complicaciones: $2500.00",
                "Cirugía de emergencia: $3500.00",
                "Honorarios cirujano cirugía menor: $400.00",
                "Honorarios cirujano cirugía mayor: $1200.00",
                "Honorarios anestesiólogo: $600.00",
                "Honorarios ayudante cirujano: $350.00",
                "Material quirúrgico básico: $180.00",
                "",
                "== REHABILITACIÓN Y TERAPIAS ==",
                "Fisioterapia sesión 45 minutos: $40.00",
                "Terapia ocupacional sesión: $45.00",
                "Fonoaudiología sesión: $50.00",
                "Psicología sesión 50 minutos: $70.00",
                "Psiquiatría consulta: $95.00",
                "Nutrición y dietética consulta: $55.00",
                "",
                "== MEDICAMENTOS Y SUMINISTROS ==",
                "Medicamentos hospitalarios por día: $120.00",
                "Oxígeno medicinal por día: $85.00",
                "Material de curación básico: $35.00",
                "Suero fisiológico por litro: $12.00"
            )
        );
    }

    private TariffDef buildHogar() {
        return new TariffDef(
            "tarifario_siniestros_hogar.pdf",
            "TARIFARIO — SEGUROS DE HOGAR Y PROPIEDAD",
            "Aplicable a: Pólizas de Hogar, Incendio, Robo, Inundación, Responsabilidad Civil | Versión 2025-01",
            List.of(
                "== MANO DE OBRA ==",
                "Plomero por hora: $35.00",
                "Electricista certificado por hora: $42.00",
                "Albañil mampostería por hora: $28.00",
                "Carpintero por hora: $35.00",
                "Pintor por hora: $25.00",
                "Cerrajero visita y servicio básico: $65.00",
                "Técnico HVAC aire acondicionado por hora: $55.00",
                "Técnico electrodomésticos por hora: $45.00",
                "",
                "== MATERIALES DE CONSTRUCCIÓN ==",
                "Pintura interior látex por galón: $28.00",
                "Pintura exterior por galón: $35.00",
                "Aplicación pintura interior por metro cuadrado: $7.00",
                "Aplicación pintura exterior por metro cuadrado: $10.00",
                "Cerámica básica instalada por metro cuadrado: $45.00",
                "Porcelanato instalado por metro cuadrado: $75.00",
                "Mortero adhesivo bolsa 25kg: $12.00",
                "Cemento estándar bolsa 42.5kg: $10.00",
                "Varilla de construcción 3/8 pulg 6 metros: $8.50",
                "Arena por metro cúbico: $35.00",
                "Grava por metro cúbico: $40.00",
                "",
                "== CARPINTERÍA Y VENTANAS ==",
                "Puerta interior básica instalada: $180.00",
                "Puerta interior premium instalada: $320.00",
                "Puerta exterior blindada instalada: $650.00",
                "Ventana aluminio básica 1x1m instalada: $220.00",
                "Ventana aluminio doble vidrio 1x1m: $380.00",
                "Marco de ventana instalado: $95.00",
                "Vidrio simple 4mm por metro cuadrado: $40.00",
                "Vidrio laminado 6mm por metro cuadrado: $85.00",
                "Vidrio templado 8mm por metro cuadrado: $130.00",
                "",
                "== PLOMERÍA ==",
                "Reparación tubería hasta 2 metros: $90.00",
                "Reemplazo tubería PVC por metro lineal: $15.00",
                "Llave de paso estándar: $35.00",
                "Llave de paso esférica: $55.00",
                "Inodoro estándar instalado: $220.00",
                "Lavabo pedestal instalado: $180.00",
                "Ducha completa instalada: $350.00",
                "Calentador de agua eléctrico 40 litros instalado: $320.00",
                "Calentador de agua a gas 6 litros instalado: $450.00",
                "Tubería gas certificada por metro lineal: $55.00",
                "",
                "== ELECTRICIDAD ==",
                "Punto de luz instalado: $65.00",
                "Tomacorriente doble instalado: $55.00",
                "Interruptor simple instalado: $45.00",
                "Breaker 15 amperios instalado: $40.00",
                "Tablero eléctrico 12 circuitos: $280.00",
                "Cable calibre 12 rollo 100 metros: $95.00",
                "Canaleta plástica por metro lineal: $8.00",
                "",
                "== IMPERMEABILIZACIÓN Y TECHOS ==",
                "Impermeabilización membrana por metro cuadrado: $22.00",
                "Impermeabilización líquida por metro cuadrado: $15.00",
                "Reparación techo metálico por metro cuadrado: $45.00",
                "Lámina asfáltica por metro cuadrado: $18.00",
                "Teja cerámica instalada por metro cuadrado: $55.00",
                "Canalón de techo por metro lineal: $25.00"
            )
        );
    }

    private TariffDef buildVida() {
        return new TariffDef(
            "tarifario_vida_accidentes_personales.pdf",
            "TARIFARIO — SEGUROS DE VIDA Y ACCIDENTES PERSONALES",
            "Aplicable a: Pólizas de Vida, Accidentes, Sepelio, Invalidez | Versión 2025-01",
            List.of(
                "== INDEMNIZACIONES POR MUERTE ==",
                "Muerte accidental capital básico: $50000.00",
                "Gastos de sepelio y funeral: $2500.00",
                "Repatriación de restos nacional: $1800.00",
                "Repatriación de restos internacional: $4500.00",
                "Ataúd básico: $800.00",
                "Ataúd premium: $1500.00",
                "Servicio funerario básico: $600.00",
                "",
                "== INVALIDEZ ==",
                "Invalidez total y permanente: $50000.00",
                "Invalidez parcial permanente por punto porcentual: $500.00",
                "Pérdida de mano dominante: $25000.00",
                "Pérdida de mano no dominante: $20000.00",
                "Pérdida de pie: $22000.00",
                "Pérdida de visión un ojo: $20000.00",
                "Pérdida de visión total bilateral: $50000.00",
                "Pérdida de audición un oído: $10000.00",
                "Pérdida de audición total bilateral: $25000.00",
                "",
                "== INCAPACIDAD TEMPORAL ==",
                "Incapacidad temporal total por semana máximo 52 semanas: $300.00",
                "Incapacidad temporal parcial por semana: $150.00",
                "Hospitalización por día máximo 365 días: $80.00",
                "",
                "== GASTOS MÉDICOS POR ACCIDENTE ==",
                "Gastos médicos de urgencia: $1500.00",
                "Cirugía de urgencia por accidente: $3000.00",
                "Fisioterapia post accidente sesión hasta 20 sesiones: $40.00",
                "Prótesis miembro superior: $4500.00",
                "Prótesis miembro inferior: $5500.00",
                "Prótesis dental por pieza: $350.00",
                "Lentes o prótesis ocular: $250.00",
                "Silla de ruedas estándar: $800.00",
                "Muletas o andadera: $120.00",
                "Órtesis según tipo: $650.00",
                "",
                "== RENTA Y BENEFICIOS ==",
                "Renta por incapacidad mensual: $1200.00",
                "Subsidio hospitalario diario: $80.00",
                "Beneficio por diagnóstico cáncer: $15000.00",
                "Beneficio por infarto agudo de miocardio: $10000.00",
                "Beneficio por accidente cerebrovascular: $10000.00"
            )
        );
    }

    private TariffDef buildGeneral() {
        return new TariffDef(
            "tarifario_general_servicios_profesionales.pdf",
            "TARIFARIO GENERAL — SERVICIOS PROFESIONALES Y PERITAJE",
            "Aplicable a: Todos los ramos de seguros. Honorarios, Transporte, Servicios | Versión 2025-01",
            List.of(
                "== HONORARIOS PROFESIONALES ==",
                "Perito tasador visita e informe: $150.00",
                "Ingeniero inspector visita e informe: $180.00",
                "Abogado por hora: $120.00",
                "Contador por hora: $90.00",
                "Consultoría técnica especializada por hora: $100.00",
                "Mediador de seguros certificado por hora: $85.00",
                "Traductor certificado por página: $40.00",
                "Notario por acta: $80.00",
                "",
                "== TRANSPORTE Y LOGÍSTICA ==",
                "Grúa vehículo liviano hasta 50 km: $80.00",
                "Grúa vehículo liviano kilómetro adicional: $1.20",
                "Grúa vehículo pesado hasta 50 km: $160.00",
                "Transporte de carga pequeña hasta 500 kg: $95.00",
                "Almacenamiento objetos por metro cúbico mes: $25.00",
                "Custodia de documentos caja por mes: $15.00",
                "Mudanza local hasta 3 ambientes: $350.00",
                "Guardería de vehículo por mes: $180.00",
                "",
                "== SERVICIOS GENERALES ==",
                "Limpieza residencial básica por hora: $20.00",
                "Limpieza industrial por metro cuadrado: $5.00",
                "Fumigación básica hasta 100 metros cuadrados: $120.00",
                "Desinfección por metro cuadrado: $8.00",
                "Remoción de escombros por metro cúbico: $45.00",
                "Lavado de alfombra por metro cuadrado: $6.00",
                "",
                "== TECNOLOGÍA Y EQUIPOS ==",
                "Reparación computadora básica: $85.00",
                "Reparación celular pantalla: $120.00",
                "Instalación sistema de seguridad básico: $450.00",
                "Cámara de seguridad instalada: $150.00",
                "UPS regulador de voltaje básico: $95.00",
                "Impresora básica reemplazo: $180.00",
                "Equipo de cómputo básico laptop: $650.00",
                "",
                "== RESPONSABILIDAD CIVIL Y LEGAL ==",
                "Gastos de defensa legal básico: $1200.00",
                "Responsabilidad civil básica límite diario: $500.00",
                "Fianza de cumplimiento básica: $300.00",
                "Gestión de reclamo administrativo: $250.00",
                "",
                "== SERVICIOS DE SALUD COMPLEMENTARIOS ==",
                "Traslado en ambulancia básico: $180.00",
                "Traslado en ambulancia con médico: $350.00",
                "Asistencia médica domiciliaria: $90.00",
                "Telemedicina consulta 24 horas: $30.00"
            )
        );
    }

    // ──────────────────────────────────── PDF BUILDER ────────────────────────────────────

    private byte[] createPdf(TariffDef def) throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType1Font fontBold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = null;
            PDPageContentStream cs = null;
            float y = 0;
            final float margin = 50;
            final float pageHeight = PDRectangle.LETTER.getHeight();

            for (int i = 0; i < def.lines().size(); i++) {
                String rawLine = def.lines().get(i);

                // New page needed
                if (page == null || y < margin + 20) {
                    if (cs != null) cs.close();

                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageHeight - margin;

                    // Title on first page only
                    if (doc.getNumberOfPages() == 1) {
                        cs.setFont(fontBold, 13);
                        cs.beginText();
                        cs.newLineAtOffset(margin, y);
                        cs.showText(def.title());
                        cs.endText();
                        y -= 18;

                        cs.setFont(fontNormal, 9);
                        cs.beginText();
                        cs.newLineAtOffset(margin, y);
                        cs.showText(def.subtitle());
                        cs.endText();
                        y -= 20;

                        // Separator line
                        cs.moveTo(margin, y);
                        cs.lineTo(PDRectangle.LETTER.getWidth() - margin, y);
                        cs.stroke();
                        y -= 14;
                    }
                }

                if (rawLine.isEmpty()) {
                    y -= 6;
                    continue;
                }

                if (rawLine.startsWith("==")) {
                    // Section header
                    y -= 4;
                    cs.setFont(fontBold, 10);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(rawLine.replace("==", "").trim());
                    cs.endText();
                    y -= 14;
                } else {
                    // Regular price line
                    cs.setFont(fontNormal, 9);
                    cs.beginText();
                    cs.newLineAtOffset(margin + 10, y);
                    String display = rawLine.length() > 95 ? rawLine.substring(0, 95) : rawLine;
                    cs.showText(display);
                    cs.endText();
                    y -= 12;
                }
            }

            if (cs != null) cs.close();

            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
