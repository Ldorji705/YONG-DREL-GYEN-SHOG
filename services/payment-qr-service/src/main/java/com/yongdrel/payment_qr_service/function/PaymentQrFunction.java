package com.yongdrel.payment_qr_service.function;
import com.google.zxing.*; import com.google.zxing.client.j2se.MatrixToImageWriter; import com.google.zxing.common.BitMatrix;
import com.yongdrel.payment_qr_service.application.port.*;
import org.springframework.context.annotation.Bean; import org.springframework.stereotype.Component;
import java.io.*; import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.util.*; import java.util.function.Function;
import static com.google.zxing.BarcodeFormat.QR_CODE;
@Component public class PaymentQrFunction{
 private final OrderTrustPort orders; private final TenantPaymentDetailsPort tenants;
 public PaymentQrFunction(OrderTrustPort orders,TenantPaymentDetailsPort tenants){this.orders=orders;this.tenants=tenants;}
 @Bean public Function<PaymentQrRequest,PaymentQrResponse> generatePaymentQr(){return this::generate;}
 PaymentQrResponse generate(PaymentQrRequest r){
  if(r==null||r.tenantId()==null||r.organizerId()==null||r.orderId()==null||r.amount()==null||r.amount().signum()<=0||r.currency()==null||r.reference()==null||r.reference().isBlank())throw new IllegalArgumentException("Invalid QR request");
  OrderTrustPort.OrderDetails order=orders.get(r.orderId(),r.tenantId()); TenantPaymentDetailsPort.PaymentDetails destination=tenants.get(r.tenantId(),r.organizerId());
  if(order==null||destination==null)throw new IllegalStateException("Trusted order or payment details unavailable");
  if(!r.orderId().equals(order.orderId())||!r.tenantId().equals(order.tenantId())||order.amount()==null||order.currency()==null||r.amount().compareTo(order.amount())!=0||!r.currency().equalsIgnoreCase(order.currency()))throw new IllegalArgumentException("Order amount or currency mismatch");
  if(!"AWAITING_PAYMENT".equals(order.status()))throw new IllegalStateException("Order is not awaiting payment");
  if(order.reference()==null||!r.reference().equals(order.reference()))throw new IllegalArgumentException("Order reference mismatch");
  if(!r.tenantId().equals(destination.tenantId())||!r.organizerId().equals(destination.organizerId())||destination.accountReference()==null||destination.accountReference().isBlank())throw new IllegalStateException("Trusted payment destination unavailable");
  String payload="YONGDREL|ORDER="+r.orderId()+"|AMOUNT="+r.amount().toPlainString()+"|CURRENCY="+r.currency().toUpperCase(Locale.ROOT)+"|REF="+r.reference()+"|DEST="+destination.accountReference();
  try{BitMatrix matrix=new MultiFormatWriter().encode(payload,QR_CODE,320,320);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);String instructions=destination.instructions()==null?"Transfer the exact amount and include the order reference.":destination.instructions();return new PaymentQrResponse(r.orderId(),r.reference(),payload,"data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()),destination.destinationName(),instructions);}catch(WriterException|IOException e){throw new IllegalStateException("QR generation failed",e);}
 }
 public record PaymentQrRequest(UUID tenantId,UUID organizerId,UUID orderId,BigDecimal amount,String currency,String reference){}
 public record PaymentQrResponse(UUID orderId,String reference,String qrData,String qrImageDataUri,String destinationName,String instructions){}
}
