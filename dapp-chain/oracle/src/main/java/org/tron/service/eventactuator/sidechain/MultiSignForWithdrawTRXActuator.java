package org.tron.service.eventactuator.sidechain;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.client.MainChainGatewayApi;
import org.tron.client.SideChainGatewayApi;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.SignUtils;
import org.tron.common.utils.WalletUtil;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Sidechain.EventMsg;
import org.tron.protos.Sidechain.EventMsg.EventType;
import org.tron.protos.Sidechain.EventMsg.TaskEnum;
import org.tron.protos.Sidechain.MultiSignForWithdrawTRXEvent;
import org.tron.service.capsule.TransactionExtensionCapsule;

@Slf4j(topic = "sideChainTask")
public class MultiSignForWithdrawTRXActuator extends MultiSignForWithdrawActuator {

  private static final String PREFIX = "withdraw_2_";
  private MultiSignForWithdrawTRXEvent event;
  @Getter
  private EventType type = EventType.MULTISIGN_FOR_WITHDRAW_TRX_EVENT;
  @Getter
  private TaskEnum taskEnum = TaskEnum.MAIN_CHAIN;

  public MultiSignForWithdrawTRXActuator(String from, String value, String nonce) {
    ByteString fromBS = ByteString.copyFrom(WalletUtil.decodeFromBase58Check(from));
    ByteString valueBS = ByteString.copyFrom(ByteArray.fromString(value));
    ByteString nonceBS = ByteString.copyFrom(ByteArray.fromString(nonce));
    this.event = MultiSignForWithdrawTRXEvent.newBuilder().setFrom(fromBS).setValue(valueBS)
        .setNonce(nonceBS).build();
  }

  public MultiSignForWithdrawTRXActuator(EventMsg eventMsg) throws InvalidProtocolBufferException {
    this.event = eventMsg.getParameter().unpack(MultiSignForWithdrawTRXEvent.class);
  }

  @Override
  public CreateRet createTransactionExtensionCapsule() {
    if (Objects.nonNull(transactionExtensionCapsule)) {
      return CreateRet.SUCCESS;
    }
    try {
      String fromStr = WalletUtil.encode58Check(event.getFrom().toByteArray());
      String valueStr = event.getValue().toStringUtf8();
      String nonceStr = event.getNonce().toStringUtf8();
      List<String> oracleSigns = SideChainGatewayApi.getWithdrawOracleSigns(nonceStr);

      logger
          .info("MultiSignForWithdrawTRXActuator, from: {}, value: {}, nonce: {}", fromStr,
              valueStr,
              nonceStr);
      Transaction tx = MainChainGatewayApi
          .multiSignForWithdrawTRXTransaction(fromStr, valueStr, nonceStr, oracleSigns);
      this.transactionExtensionCapsule = new TransactionExtensionCapsule(PREFIX + nonceStr, tx,
          getDelay(fromStr, valueStr, nonceStr, oracleSigns));
      return CreateRet.SUCCESS;
    } catch (Exception e) {
      logger.error("when create transaction extension capsule", e);
      return CreateRet.FAIL;
    }
  }

  private long getDelay(String fromStr, String valueStr,
      String nonceStr, List<String> oracleSigns) {
    String ownSign = SideChainGatewayApi.getWithdrawTRXSign(fromStr, valueStr, nonceStr);
    return SignUtils.getDelay(ownSign, oracleSigns);
  }

  @Override
  public EventMsg getMessage() {
    return EventMsg.newBuilder().setParameter(Any.pack(this.event)).setType(getType())
        .setTaskEnum(getTaskEnum()).build();
  }

  @Override
  public byte[] getNonceKey() {
    return ByteArray.fromString(PREFIX + event.getNonce().toStringUtf8());
  }

  @Override
  public byte[] getNonce() {
    return event.getNonce().toByteArray();
  }

  @Override
  public String getWithdrawDataHash() {
    String fromStr = WalletUtil.encode58Check(event.getFrom().toByteArray());
    String valueStr = event.getValue().toStringUtf8();
    String nonceStr = event.getNonce().toStringUtf8();
    return ByteArray
        .toHexString(SideChainGatewayApi.getWithdrawTRXDataHash(fromStr, valueStr, nonceStr));
  }
}