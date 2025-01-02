import { Octokit } from "octokit";
import dotenv from 'dotenv';
import fs from 'fs';
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';

dotenv.config();

const octokit = new Octokit({
    auth: process.env.GITHUB_TOKEN,
});

const ACCEPT_HEADER = 'application/vnd.github.v3.text-match+json';

const waitForRateLimitReset =         async () => {
    const { data: { resources: { core } } } = await octokit.rest.rateLimit.get();
    if (core.remaining === 0) {
        const resetTime = new Date(core.reset * 1000);
        const waitTime = resetTime - new Date();
        console.log(`Rate limit exceeded. Waiting for ${waitTime / 1000} seconds.`);
        await new Promise(resolve => setTimeout(resolve, waitTime));
    }
};

const escapeHtml = (unsafe) => {
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
};

const searchCode = async (cdnString, page) => {
    await waitForRateLimitReset();
    return octokit.rest.search.code({
        q: `${cdnString} org:companieshouse`,
        per_page: 100,
        page,
        headers: {
            accept: ACCEPT_HEADER
        }
    });
};

const getContent = async (owner, repo, path) => {
    await waitForRateLimitReset();
    const response = await octokit.rest.repos.getContent({
        owner,
        repo,
        path,
        mediaType: {
            format: 'raw',
        },
    });
    return response.data;
};

const processLines = (lines, pattern, repo, path) => {
    const assets = [];
    lines.forEach((line, index) => {
        const match = line.trim().match(pattern);
        if (match) {
            assets.push({
                repository: repo,
                filepath: path,
                linenumber: index + 1,
                line: escapeHtml(line.trim()),
                name: match[1]
            });
        }
    });
    return assets;
};

const getAllResults = async (cdnString) => {
    let allResults = [];
    const firstResponse = await searchCode(cdnString, 1);
    allResults.push(...firstResponse.data.items);

    const totalCount = firstResponse.data.total_count;
    const totalPages = Math.ceil(totalCount / 100);
    console.log(`${totalCount} potential matches found with ${cdnString}`);

    const pagePromises = [];
    for (let page = 2; page <= totalPages; page++) {
        pagePromises.push(searchCode(cdnString, page));
    }

    const pageResponses = await Promise.all(pagePromises);
    pageResponses.forEach(response => {
        allResults.push(...response.data.items);
    });

    return allResults;
};

const processRecords = async (records, pattern, fileChecklist) => {
    const identifiedAssets = [];

    const contentPromises = records.map(async (record) => {
        const { owner: { login: owner }, name: repo } = record.repository;
        const { path } = record;

        if (!fileChecklist.has(`${repo}/${path}`)) {
            const content = await getContent(owner, repo, path);
            const lines = content.split('\n');
            const assets = processLines(lines, pattern, repo, path);
            identifiedAssets.push(...assets);
            fileChecklist.add(`${repo}/${path}`);
        }
    });

    await Promise.all(contentPromises);
    return identifiedAssets;
};

const getFilesFromAssetFolders = async (searchStrings) => {
    const fileChecklist = new Set();
    const pattern = /<script\s+[^>]*src=["'][^"']*\/([^"']+\.js)["\']/i;

    const allResultsPromises = searchStrings.map(cdnString => getAllResults(cdnString));
    const allResultsArrays = await Promise.all(allResultsPromises);

    const allResults = allResultsArrays.flat();
    const identifiedAssets = await processRecords(allResults, pattern, fileChecklist);

    console.log(identifiedAssets.length);
    return identifiedAssets;
};

const convertToMarkdownTable = (data) => {
    const headers = ['Repository', 'File Path', 'Line Number', 'Line', 'Asset Name'];
    const headerRow = `| ${headers.join(' | ')} |`;
    const separatorRow = `| ${headers.map(() => '---').join(' | ')} |`;
    const dataRows = data.map(row => `| ${row.repository} | ${row.filepath} | ${row.linenumber} | ${row.line} | ${row.name} |`);

    return [headerRow, separatorRow, ...dataRows].join('\n');
};

const argv = yargs(hideBin(process.argv)).array('searchStrings').argv;

(async () => {
    const searchStrings = argv.searchStrings;
    if (!searchStrings || searchStrings.length === 0) {
        console.error('Please provide an array of CDN strings using the --searchStrings option.');
        process.exit(1);
    }

    const results = await getFilesFromAssetFolders(searchStrings);

    if (results.length > 0) {
        const markdownContent = convertToMarkdownTable(results);
        fs.writeFileSync('identified_assets.md', markdownContent, 'utf8');
        console.log('Markdown file has been saved.');
    }
})();