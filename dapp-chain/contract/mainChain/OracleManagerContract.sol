pragma solidity ^0.4.24;

import "./ownership/Ownable.sol";
import "../common/ECVerify.sol";


contract OracleManagerContract is Ownable {
    using ECVerify for bytes32;


    uint256 public numOracles;
    uint256 public numCommonOracles;
    mapping(address => uint256) public oracleIndex;
    mapping(uint256 => address) public indexOracle;
    mapping(address => SignMsg)  delegateSigns;
    mapping(uint256 => mapping(bytes32 => SignMsg)) withdrawMultiSignList;

    address logicAddress;
    bool pause;
    bool stop;

    struct SignMsg {
        uint256 signedOracleFlag;
        uint256 countSign;
        bool success;
    }

    event NewOracles(address oracle);
    event LogicAddressChanged(address oldAddress, address newAddress);

    modifier onlyOracle() {require(oracleIndex[msg.sender] > 0, "not oracle");
        _;}
    modifier onlyNotPause() {require(!pause, "is pause");
        _;}
    modifier onlyNotStop() {require(!stop, "is stop");
        _;}

    modifier goDelegateCall() {
        if (logicAddress != address(0)) {
            logicAddress.delegatecall(msg.data);
            return;
        }
        _;
    }

    modifier checkForTrc10(uint64 tokenId, uint64 tokenValue) {
        require(tokenId == uint64(msg.tokenid), "tokenId != msg.tokenid");
        require(tokenValue == uint64(msg.tokenvalue), "tokenValue != msg.tokenvalue");
        _;
    }

    function checkOracles(bytes32 dataHash, uint256 nonce, bytes[] sigList) internal returns (bool) {
        SignMsg storage signMsg = withdrawMultiSignList[nonce][dataHash];

        for (uint256 i = 0; i < sigList.length; i++) {
            address _oracle = dataHash.recover(sigList[i]);
            if (oracleIndex[_oracle] == 0) {// not oracle
                continue;
            }
            uint256 signed = (1 << (oracleIndex[_oracle] - 1)) & signMsg.signedOracleFlag;
            if (signed == 0) {// not signed
                signMsg.signedOracleFlag = (1 << (oracleIndex[_oracle] - 1)) | signMsg.signedOracleFlag;
                signMsg.countSign++;
            }
        }

        if (signMsg.countSign > numCommonOracles && !signMsg.success) {
            signMsg.success = true;
            return true;
        }
        return false;
    }

    function addOracle(address _oracle) public onlyOwner {
        require(oracleIndex[_oracle] == 0, "this address is already oracle");

        uint256 i;
        for (i = 1; i <= 256; i++) {
            if (indexOracle[i] == address(0)) {
                break;
            }
        }
        require(i <= 256, "oracle num > 256");
        oracleIndex[_oracle] = i;
        indexOracle[i] = _oracle;

        numOracles++;
        numCommonOracles = numOracles * 2 / 3;
    }

    function delOracle(address _oracle) public onlyOwner {
        require(oracleIndex[_oracle] > 0, "this address is not oracle");

        indexOracle[oracleIndex[_oracle]] = address(0);
        oracleIndex[_oracle] = 0;

        numOracles--;
        numCommonOracles = numOracles * 2 / 3;
    }

    function setDelegateAddress(address newAddress) public onlyOracle {
        bool needDelegate = multiSignForDelegate(newAddress);
        if (needDelegate) {
            emit LogicAddressChanged(logicAddress, newAddress);
            logicAddress = newAddress;
        }
    }

    function setPause(bool status) public onlyOwner {
        pause = status;
    }

    function setStop(bool status) public onlyOwner {
        stop = status;
    }

    function multiSignForDelegate(address newAddress) internal returns (bool) {
        SignMsg storage signMsg = delegateSigns[newAddress];
        uint256 signed = (1 << (oracleIndex[msg.sender] - 1)) & signMsg.signedOracleFlag;
        if (signed > 0) {
            // have signed
            return false;
        }

        signMsg.signedOracleFlag = (1 << (oracleIndex[msg.sender] - 1)) | signMsg.signedOracleFlag;
        signMsg.countSign++;

        if (signMsg.countSign > numCommonOracles && !signMsg.success) {
            signMsg.success = true;
            return true;
        }
        return false;
    }

    function withdrawDone(bytes32 dataHash, uint256 nonce) view public returns (bool r) {
        r = withdrawMultiSignList[nonce][dataHash].success;
    }

    function isOracle(address _oracle) view public returns (bool) {
        return oracleIndex[_oracle] > 0;
    }
}