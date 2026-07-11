package com.talexck.gameVoting.velocity;

final class ProtocolVersionMapper {
  private ProtocolVersionMapper() {}

  static String versionName(int protocol) {
    return switch (protocol) {
      case 47 -> "1.8";
      case 107 -> "1.9";
      case 108 -> "1.9.1";
      case 109 -> "1.9.2";
      case 110 -> "1.9.4";
      case 210 -> "1.10";
      case 315 -> "1.11";
      case 316 -> "1.11.1/1.11.2";
      case 335 -> "1.12";
      case 338 -> "1.12.1";
      case 340 -> "1.12.2";
      case 393 -> "1.13";
      case 401 -> "1.13.1";
      case 404 -> "1.13.2";
      case 477 -> "1.14";
      case 480 -> "1.14.1";
      case 485 -> "1.14.2";
      case 490 -> "1.14.3";
      case 498 -> "1.14.4";
      case 573 -> "1.15";
      case 575 -> "1.15.1";
      case 578 -> "1.15.2";
      case 735 -> "1.16";
      case 736 -> "1.16.1";
      case 751 -> "1.16.2";
      case 753 -> "1.16.3";
      case 754 -> "1.16.4/1.16.5";
      case 755 -> "1.17";
      case 756 -> "1.17.1";
      case 757 -> "1.18/1.18.1";
      case 758 -> "1.18.2";
      case 759 -> "1.19";
      case 760 -> "1.19.1/1.19.2";
      case 761 -> "1.19.3";
      case 762 -> "1.19.4";
      case 763 -> "1.20/1.20.1";
      case 764 -> "1.20.2";
      case 765 -> "1.20.3/1.20.4";
      case 766 -> "1.20.5/1.20.6";
      case 767 -> "1.21/1.21.1";
      case 768 -> "1.21.2/1.21.3";
      case 769 -> "1.21.4";
      case 770 -> "1.21.5";
      case 771 -> "1.21.6";
      case 772 -> "1.21.7/1.21.8";
      case 773 -> "1.21.9/1.21.10";
      case 774 -> "1.21.11";
      case 775 -> "26.1/26.1.1/26.1.2";
      case 776 -> "26.2";
      default -> null;
    };
  }
}
