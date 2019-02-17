package com.dhpcs.liquidity.model

import java.time.Instant

import com.dhpcs.liquidity.proto
import com.dhpcs.liquidity.proto.binding.ProtoBinding
import com.dhpcs.liquidity.proto.binding.ProtoBindings._
import shapeless.cachedImplicit

object ProtoBindings {

  implicit def setProtoBinding[S, P, C](
      implicit protoBinding: ProtoBinding[S, P, C])
    : ProtoBinding[Set[S], Seq[P], C] =
    ProtoBinding.instance(
      (set, context) => set.map(protoBinding.asProto(_)(context)).toSeq,
      (set, context) => set.map(protoBinding.asScala(_)(context)).toSet)

  implicit final val PublicKeyProtoBinding
    : ProtoBinding[PublicKey, com.google.protobuf.ByteString, Any] =
    ProtoBinding.instance(
      (publicKey, _) =>
        com.google.protobuf.ByteString.copyFrom(publicKey.value.asByteBuffer()),
      (publicKeyBytes, _) =>
        PublicKey(okio.ByteString.of(publicKeyBytes.asReadOnlyByteBuffer()))
    )

  implicit final val InstantProtoBinding: ProtoBinding[Instant, Long, Any] =
    ProtoBinding.instance((instant, _) => instant.toEpochMilli,
                          (epochMillis, _) => Instant.ofEpochMilli(epochMillis))

  implicit final val MemberIdProtoBinding: ProtoBinding[MemberId, String, Any] =
    ProtoBinding.instance((memberId, _) => memberId.value,
                          (id, _) => MemberId(id))

  implicit final val MemberProtoBinding
    : ProtoBinding[Member, proto.model.Member, Any] = cachedImplicit

  implicit final val AccountIdProtoBinding
    : ProtoBinding[AccountId, String, Any] =
    ProtoBinding.instance((accountId, _) => accountId.value,
                          (id, _) => AccountId(id))

  implicit final val AccountProtoBinding
    : ProtoBinding[Account, proto.model.Account, Any] = cachedImplicit

  implicit final val TransactionIdProtoBinding
    : ProtoBinding[TransactionId, String, Any] =
    ProtoBinding.instance((transactionId, _) => transactionId.value,
                          (id, _) => TransactionId(id))

  implicit final val BigDecimalProtoBinding
    : ProtoBinding[BigDecimal, String, Any] =
    ProtoBinding.instance(
      (bigDecimal, _) => bigDecimal.toString(),
      (numberString, _) => BigDecimal(numberString)
    )

  implicit final val TransactionProtoBinding
    : ProtoBinding[Transaction, proto.model.Transaction, Any] = cachedImplicit

  implicit final val ZoneIdProtoBinding: ProtoBinding[ZoneId, String, Any] =
    ProtoBinding.instance((zoneId, _) => zoneId.value.toString,
                          (id, _) => ZoneId(id))

  trait EntityIdExtractor[E, I] {
    def extractId(entity: E): I
  }

  object EntityIdExtractor {
    def instance[E, I](apply: E => I): EntityIdExtractor[E, I] =
      (e: E) => apply(e)
  }

  implicit final val MemberIdExtractor: EntityIdExtractor[Member, MemberId] =
    EntityIdExtractor.instance[Member, MemberId](_.id)

  implicit final val AccountIdExtractor: EntityIdExtractor[Account, AccountId] =
    EntityIdExtractor.instance[Account, AccountId](_.id)

  implicit final val TransactionIdExtractor
    : EntityIdExtractor[Transaction, TransactionId] =
    EntityIdExtractor.instance[Transaction, TransactionId](_.id)

  implicit def idMapProtoBinding[SK, SV, P, C](
      implicit protoBinding: ProtoBinding[SV, P, C],
      entityIdExtractor: EntityIdExtractor[SV, SK])
    : ProtoBinding[Map[SK, SV], Seq[P], C] =
    ProtoBinding.instance(
      (map, context) => map.values.map(protoBinding.asProto(_)(context)).toSeq,
      (seq, context) =>
        seq
          .map(protoBinding.asScala(_)(context))
          .map(s => entityIdExtractor.extractId(s) -> s)
          .toMap
    )

  implicit def mapProtoBinding[SK, SV, PK, PV, C](
      implicit kBinding: ProtoBinding[SK, PK, C],
      vBinding: ProtoBinding[SV, PV, C])
    : ProtoBinding[Map[SK, SV], Map[PK, PV], C] =
    ProtoBinding.instance(
      (map, context) =>
        map.map {
          case (sk, sv) =>
            (kBinding.asProto(sk)(context), vBinding.asProto(sv)(context))
      },
      (map, context) =>
        map.map {
          case (pk, pv) =>
            (kBinding.asScala(pk)(context), vBinding.asScala(pv)(context))
      }
    )

  implicit final val ZoneProtoBinding
    : ProtoBinding[Zone, proto.model.Zone, Any] = cachedImplicit

}
