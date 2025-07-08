// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title VoteChain
 * @dev Contrato para gestionar votaciones seguras y transparentes con verificación en blockchain
 */
contract VoteChain {

    // Estructura para almacenar un voto
    struct Vote {
        uint256 votingId;
        uint256 userId;
        uint256 optionId;
        string voteHash;
        uint256 timestamp;
        bool exists;
    }

    // Estructura para almacenar una votación
    struct Voting {
        uint256 id;
        string title;
        uint256 startTime;
        uint256 endTime;
        bool active;
        address creator;
        bool exists;
    }

    // Estructura para almacenar estadísticas de votación
    struct VotingStats {
        uint256 totalVotes;
        mapping(uint256 => uint256) optionVotes; // optionId => voteCount
    }

    // Mappings para almacenar datos
    mapping(uint256 => Voting) public votings;
    mapping(string => Vote) public votes;
    mapping(uint256 => mapping(uint256 => bool)) public hasVoted; // votingId => userId => bool
    mapping(uint256 => VotingStats) private votingStats; // votingId => stats

    uint256 public votingCounter;
    address public owner;

    // Eventos
    event VotingCreated(uint256 indexed votingId, string title, address creator);
    event VoteCast(uint256 indexed votingId, uint256 indexed userId, uint256 indexed optionId, string voteHash);
    event VotingStatusChanged(uint256 indexed votingId, bool active);
    event VotingClosed(uint256 indexed votingId, uint256 totalVotes);

    // Modificadores
    modifier onlyOwner() {
        require(msg.sender == owner, "Solo el propietario puede ejecutar esta funcion");
        _;
    }

    modifier votingExists(uint256 _votingId) {
        require(votings[_votingId].exists, "La votacion no existe");
        _;
    }

    modifier votingActive(uint256 _votingId) {
        require(votings[_votingId].exists, "La votacion no existe");
        require(votings[_votingId].active, "La votacion no esta activa");
        _;
    }

    constructor() {
        owner = msg.sender;
        votingCounter = 0;
    }

    // Crear una nueva votación
    function createVoting(
        string memory _title,
        uint256 _startTime,
        uint256 _endTime
    ) public returns (uint256) {
        require(_startTime < _endTime, "Rango de tiempo invalido");

        votingCounter++;

        votings[votingCounter] = Voting({
            id: votingCounter,
            title: _title,
            startTime: _startTime,
            endTime: _endTime,
            active: true,
            creator: msg.sender,
            exists: true
        });

        // Inicializar estadísticas de votación
        votingStats[votingCounter].totalVotes = 0;

        emit VotingCreated(votingCounter, _title, msg.sender);
        return votingCounter;
    }

    // Emitir un voto
    function castVote(
        uint256 _votingId,
        uint256 _userId,
        uint256 _optionId,
        string memory _voteHash
    ) public votingActive(_votingId) {
        require(!hasVoted[_votingId][_userId], "El usuario ya ha votado");
        require(bytes(_voteHash).length > 0, "El hash del voto no puede estar vacio");

        votes[_voteHash] = Vote({
            votingId: _votingId,
            userId: _userId,
            optionId: _optionId,
            voteHash: _voteHash,
            timestamp: block.timestamp,
            exists: true
        });

        hasVoted[_votingId][_userId] = true;

        // Actualizar estadísticas
        votingStats[_votingId].totalVotes++;
        votingStats[_votingId].optionVotes[_optionId]++;

        emit VoteCast(_votingId, _userId, _optionId, _voteHash);
    }

    // Verificar un voto
    function verifyVote(string memory _voteHash) public view returns (bool exists, uint256 votingId, uint256 optionId, uint256 timestamp) {
        Vote storage vote = votes[_voteHash];
        return (vote.exists, vote.votingId, vote.optionId, vote.timestamp);
    }

    // Establecer estado de votación (activa/inactiva)
    function setVotingStatus(uint256 _votingId, bool _active) public votingExists(_votingId) {
        require(msg.sender == owner || msg.sender == votings[_votingId].creator, "No autorizado");

        votings[_votingId].active = _active;
        emit VotingStatusChanged(_votingId, _active);

        // Si se está desactivando la votación, emitir el evento de cierre
        if (!_active) {
            emit VotingClosed(_votingId, votingStats[_votingId].totalVotes);
        }
    }

    // Verificar si un usuario ya ha votado
    function userHasVoted(uint256 _votingId, uint256 _userId) public view returns (bool) {
        return hasVoted[_votingId][_userId];
    }

    // Verificar si una votación existe
    function checkVotingExists(uint256 _votingId) public view returns (bool) {
        return votings[_votingId].exists;
    }

    // Obtener detalles completos de una votación
    function getVotingDetails(uint256 _votingId) public view
    votingExists(_votingId)
    returns (
        string memory title,
        uint256 startTime,
        uint256 endTime,
        bool active,
        address creator,
        uint256 totalVotes
    )
    {
        Voting storage voting = votings[_votingId];
        return (
            voting.title,
            voting.startTime,
            voting.endTime,
            voting.active,
            voting.creator,
            votingStats[_votingId].totalVotes
        );
    }

    // Obtener el recuento de votos para una opción específica
    function getOptionVoteCount(uint256 _votingId, uint256 _optionId) public view
    votingExists(_votingId)
    returns (uint256)
    {
        return votingStats[_votingId].optionVotes[_optionId];
    }

    // Verificar si una votación ha finalizado según su tiempo
    function isVotingTimeEnded(uint256 _votingId) public view
    votingExists(_votingId)
    returns (bool)
    {
        return (block.timestamp >= votings[_votingId].endTime);
    }

    // Finalizar automáticamente votaciones que han excedido su tiempo
    function closeExpiredVotings(uint256[] memory _votingIds) public {
        for (uint i = 0; i < _votingIds.length; i++) {
            uint256 votingId = _votingIds[i];
            if (votings[votingId].exists &&
            votings[votingId].active &&
                block.timestamp >= votings[votingId].endTime) {

                votings[votingId].active = false;
                emit VotingStatusChanged(votingId, false);
                emit VotingClosed(votingId, votingStats[votingId].totalVotes);
            }
        }
    }

    // Transferir propiedad del contrato
    function transferOwnership(address _newOwner) public onlyOwner {
        require(_newOwner != address(0), "Nueva direccion no puede ser cero");
        owner = _newOwner;
    }
}
