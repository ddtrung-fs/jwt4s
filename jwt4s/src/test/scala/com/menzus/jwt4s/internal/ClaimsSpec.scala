package com.menzus.jwt4s.internal

import com.menzus.jwt4s.DummyClock
import com.menzus.jwt4s.DummySettings
import com.menzus.jwt4s.error.ExpiredExpClaim
import com.menzus.jwt4s.error.FailedToParseClaims
import com.menzus.jwt4s.error.FutureIatClaim
import com.menzus.jwt4s.error.InvalidAudClaim
import com.menzus.jwt4s.error.InvalidBase64Format
import com.menzus.jwt4s.error.InvalidIssClaim
import com.menzus.jwt4s.error.InvalidLifeTime
import com.menzus.jwt4s.error.NoAudClaimProvided
import com.menzus.jwt4s.error.NoExpClaimProvided
import com.menzus.jwt4s.error.NoIatClaimProvided
import com.menzus.jwt4s.error.NoIssClaimProvided
import com.menzus.jwt4s.error.NoSubClaimProvided
import com.menzus.jwt4s.internal.Payload.createClaimsFor
import com.menzus.jwt4s.internal.Payload.verifyAndExtractClaims
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ClaimsSpec extends WordSpec with Matchers {

  implicit val clock = DummyClock.fixedClock
  implicit val signerSettings = DummySettings.signerSettings
  implicit val verifierSettings = DummySettings.verifierSettings

  "createClaimsFor" should {

    "create id claims for the subject and roles" in {

      createClaimsFor("subject", Set("admin")) shouldBe
        asBase64("""{"iss":"issuer","sub":"subject","aud":"audience","exp":1,"iat":0,"roles":["admin"]}""")
    }

    "create id claims for the subject without roles" in {

      createClaimsFor("subject", Set.empty) shouldBe
        asBase64("""{"iss":"issuer","sub":"subject","aud":"audience","exp":1,"iat":0}""")
    }
  }

  "verifyAndExtractClaims" should {

    "accept and return valid claims" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TMinus1,"exp":$TPlus1,"roles":["role1","role2"]}""")
      ) shouldBe Right(
        Claims(
          iss = "issuer",
          sub = "subject",
          aud = "audience",
          exp = TPlus1,
          iat = TMinus1,
          roles = Set("role1", "role2")
        )
      )
    }

    "accept iat within the tolerance" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TPlus1,"exp":$TPlus2}""")
      ) shouldBe Right(
        Claims(
          iss = "issuer",
          sub = "subject",
          aud = "audience",
          exp = TPlus2,
          iat = TPlus1,
          roles = Set.empty
        )
      )
    }


    "accept exp within the tolerance" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TMinus2,"exp":$TMinus1}""")
      ) shouldBe Right(
        Claims(
          iss = "issuer",
          sub = "subject",
          aud = "audience",
          exp = TMinus1,
          iat = TMinus2,
          roles = Set.empty
        )
      )
    }

    "reject invalid json claims" in {

      verifyAndExtractClaims(asBase64(s"""not json""")) shouldBe Left(FailedToParseClaims("""not json"""))
    }

    "reject non base64 payload" in {

      verifyAndExtractClaims("non base64") shouldBe Left(InvalidBase64Format("non base64"))
    }

    "reject header with missing subject" in {

      verifyAndExtractClaims(asBase64(s"""{"aud":"audience","iss":"issuer","iat":-1,"exp":1}""")) shouldBe
        Left(NoSubClaimProvided)
    }

    "reject header with missing audience" in {

      verifyAndExtractClaims(asBase64(s"""{"sub":"subject","iss":"issuer","iat":-1,"exp":1}""")) shouldBe
        Left(NoAudClaimProvided)
    }

    "reject header with wrong audience" in {

      verifyAndExtractClaims(asBase64(s"""{"aud":"other audience","sub":"subject","iss":"issuer","iat":-1,"exp":1}""")) shouldBe
        Left(InvalidAudClaim("other audience"))
    }

    "reject header with missing issuer" in {

      verifyAndExtractClaims(asBase64(s"""{"aud":"audience","sub":"subject","iat":-1,"exp":1}""")) shouldBe
        Left(NoIssClaimProvided)
    }

    "reject header with wrong issuer" in {

      verifyAndExtractClaims(asBase64(s"""{"aud":"audience","sub":"subject","iss":"other issuer","iat":-1,"exp":1}""")) shouldBe
        Left(InvalidIssClaim("other issuer"))
    }

    "reject header with missing exp" in {

      verifyAndExtractClaims(asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":-1}""")) shouldBe
        Left(NoExpClaimProvided)
    }

    "reject header with out of tolerance expired exp" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TMinus3,"exp":$TMinus2}""")) shouldBe
        Left(ExpiredExpClaim(TMinus2, T0))
    }

    "reject header with missing iat" in {

      verifyAndExtractClaims(asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","exp":$TPlus1}""")) shouldBe
        Left(NoIatClaimProvided)
    }

    "reject header with out of tolerance future iat" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TPlus2,"exp":$TPlus3}""")) shouldBe
        Left(FutureIatClaim(TPlus2, T0))
    }

    "reject header with too long life time" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TMinus2,"exp":$TPlus2}""")) shouldBe
        Left(InvalidLifeTime)
    }

    "reject header if iat is after exp" in {

      verifyAndExtractClaims(
        asBase64(s"""{"aud":"audience","sub":"subject","iss":"issuer","iat":$TPlus1,"exp":$TMinus1}""")) shouldBe
        Left(InvalidLifeTime)
    }
  }

  val TMinus3 = -3
  val TMinus2 = -2
  val TMinus1 = -1
  val T0      = 0
  val TPlus1  = 1
  val TPlus2  = 2
  val TPlus3  = 3
}