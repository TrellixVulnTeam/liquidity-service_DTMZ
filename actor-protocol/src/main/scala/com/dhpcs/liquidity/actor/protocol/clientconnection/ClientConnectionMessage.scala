package com.dhpcs.liquidity.actor.protocol.clientconnection

sealed abstract class ClientConnectionMessage extends Serializable

final case class MessageReceivedConfirmation(deliveryId: Long) extends ClientConnectionMessage